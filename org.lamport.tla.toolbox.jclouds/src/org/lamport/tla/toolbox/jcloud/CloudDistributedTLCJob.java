package org.lamport.tla.toolbox.jcloud;

import static com.google.common.base.Predicates.not;
import static org.jclouds.compute.predicates.NodePredicates.TERMINATED;
import static org.jclouds.compute.predicates.NodePredicates.inGroup;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.reference.AWSEC2Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.io.Payload;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.lamport.tla.toolbox.tool.tlc.job.ITLCJobStatus;
import org.lamport.tla.toolbox.tool.tlc.job.TLCJobFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;

public class CloudDistributedTLCJob extends Job {
	
	private final String identity = System.getenv("AWS_ACCESS_KEY_ID");
	private final String credential = System
			.getenv("AWS_SECRET_ACCESS_KEY");
	
	/**
	 * The groupName has to be unique per job. This is how cloud instances are
	 * associated to this job. If two jobs use the same groupName, the will talk
	 * to the same set of nodes.
	 */
	private final String groupNameUUID;
	private final Path modelPath;
	private final int nodes = 1; //TODO only supports launching TLC on a single node for now
	private final Properties props;
	private final CloudTLCInstanceParameters params;

	public CloudDistributedTLCJob(String aName, File aModelFolder,
			int numberOfWorkers, final Properties properties, CloudTLCInstanceParameters params) {
		super(aName);
		this.params = params;
		groupNameUUID = aName + "-" + UUID.randomUUID().toString();
		props = properties;
		modelPath = aModelFolder.toPath();
	}
	
	// http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/AWSCredentials.html
	private boolean validateAWSCredentials() {
		// must not be null
		if (identity != null && credential != null) {
			// identity always starts with "AIKA" and has 20 chars
			if (identity.matches("AKIA[a-zA-Z0-9]{16}")) {
				// secret has 40 chars
				return credential.length() == 40;
			}
		}
		return false;
	}

	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		monitor.beginTask("Starting TLC model checker in the cloud", 85);
		
		// Validate credentials and fail fast if null or syntactically incorrect
		if (!validateAWSCredentials()) {
			return new Status(
					Status.ERROR,
					"org.lamport.tla.toolbox.jcloud",
					"Invalid credentials, please check the environment variables "
							+ "(AWS_ACCESS_KEY_ID & AWS_SECRET_ACCESS_KEY) are correctly "
							+ "set up and picked up by the Toolbox.");
		}
		
		ComputeServiceContext context = null;
		try {
			final Payload jarPayLoad = PayloadHelper
					.appendModel2Jar(modelPath,
							props.getProperty(TLCJobFactory.MAIN_CLASS), props,
							monitor);
			// User has canceled the job, thus stop it (there will be more
			// cancelled checks down below).
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			// example of specific properties, in this case optimizing image
			// list to only amazon supplied
			final Properties properties = new Properties();
			properties.setProperty(AWSEC2Constants.PROPERTY_EC2_AMI_QUERY,
					params.getOwnerId());

			// Create compute environment in the cloud and inject an ssh
			// implementation. ssh is our means of communicating with the node.
			final Iterable<AbstractModule> modules = ImmutableSet
					.<AbstractModule> of(new SshjSshClientModule(), new SLF4JLoggingModule());

			final ContextBuilder builder = ContextBuilder
					.newBuilder(params.getCloudProvier())
					.credentials(identity, credential).modules(modules)
					.overrides(properties);

			monitor.subTask("Initializing " + builder.getApiMetadata().getName());
			context = builder.buildView(ComputeServiceContext.class);
			final ComputeService compute = context.getComputeService();
			monitor.worked(10);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			
			// start a node, but first configure it
			final TemplateOptions templateOptions = compute.templateOptions();
			
			// Open up node's firewall to allow http traffic in. This allows users to 
			// look at the munin/ stats generated for the OS as well as TLC specifically.
			// (See below where munin gets installed manually)
			// This now makes us dependent on EC2 (for now)
			templateOptions.inboundPorts(22, 80);
			
			// note this will create a user with the same name as you on the
			// node. ex. you can connect via ssh public IP
			templateOptions.runScript(AdminAccess.standard());
			
            final TemplateBuilder templateBuilder = compute.templateBuilder();
            templateBuilder.options(templateOptions);
            templateBuilder.imageId(params.getImageId());
            templateBuilder.hardwareId(params.getHardwareId());

            // Everything configured, now launch node
            monitor.subTask("Starting " + nodes + " instance(s).");
			final Set<? extends NodeMetadata> createNodesInGroup;
			createNodesInGroup = compute.createNodesInGroup(groupNameUUID,
					nodes, templateBuilder.build());
			monitor.worked(20);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

            // Install Java
			monitor.subTask("Provisioning Java on all node(s)");
            Statement installOpenJDK = InstallJDK.fromOpenJDK();
			compute.runScriptOnNodesMatching(inGroup(groupNameUUID),
					installOpenJDK);
			monitor.worked(20);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			
			// Install custom tailored jmx2munin to monitor the TLC process. Can
			// either monitor standalone tlc2.TLC or TLCServer.
			monitor.subTask("Provisioning TLC Monitoring on all node(s)");
			compute.runScriptOnNodesMatching(
					inGroup(groupNameUUID),
					// Never be prompted for input
					exec("export DEBIAN_FRONTEND=noninteractive && "
							// Download jmx2munin from the INRIA host
							// TODO make it part of Toolbox and upload from
							// there (it's tiny 48kb anyway) instead.
							// This needs some better build-time integration
							// between TLA and jmx2munin (probably best to make
							// jmx2munin a submodule of the TLA git repo).
							+ "wget https://lemmy.github.com/jmx2munin/jmx2munin_1.0_all.deb && "
//							+ "wget http://tla.msr-inria.inria.fr/jmx2munin/jmx2munin_1.0_all.deb && "
							// Install jmx2munin into the system
							+ "dpkg -i jmx2munin_1.0_all.deb ; "
							// Force apt to download and install the
							// missing dependencies of jmx2munin without
							// user interaction
							+ "apt-get install -fy && "
							// screen is needed to allow us to re-attach
							// to the TLC process if logged in to the
							// instance directly (it's probably already
							// installed).
							+ "apt-get install screen -y"),
					new TemplateOptions().runAsRoot(true).wrapInInitScript(
							false));			
			monitor.worked(10);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			
			// Create /mnt/tlc and change permission to be world writable
			// Requires package 'apache2' to be already installed. apache2
			// creates /var/www/html.
			monitor.subTask("Creating a TLC environment on all node(s)");
			compute.runScriptOnNodesMatching(
					inGroup(groupNameUUID),
					exec("mkdir /mnt/tlc/ && "
							+ "chmod 777 /mnt/tlc/ && "
							+ "ln -s /tmp/MC.out /var/www/html/MC.out && "
							+ "ln -s /tmp/MC.err /var/www/html/MC.err"),
					new TemplateOptions().runAsRoot(true).wrapInInitScript(
							false));
			monitor.worked(5);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			// TODO CloudD...TLC currently only supports a single tlc2.TLC
			// process. Thus the Predicate and isMaster check is not used for
			// the moment.
			// Choose one of the nodes to be the master and create an
			// identifying predicate.
			monitor.subTask("Copying tla2tools.jar to master node");
			final NodeMetadata master = Iterables.getLast(createNodesInGroup);
			final Predicate<NodeMetadata> isMaster = new Predicate<NodeMetadata>() {
				@Override
				public boolean apply(NodeMetadata nodeMetadata) {
					return nodeMetadata.equals(master);
				};
			};
			// Copy tlatools.jar to _one_ remote host (do not exhaust upload of
			// the machine running the toolbox)
			SshClient sshClient = context.utils().sshForNode().apply(master);
			sshClient.put("/mnt/tlc/tla2tools.jar",	jarPayLoad);
			sshClient.disconnect();
			monitor.worked(10);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			// Run model checker master
			monitor.subTask("Starting TLC model checker process on the master node (in background)");
			compute.runScriptOnNodesMatching(
					isMaster,
					// "/mnt/tlc" is on the ephemeral and thus faster storage of the
					// instance.
					exec("cd /mnt/tlc/ && "
							// Execute TLC (java) process inside screen
							// and shutdown on TLC's completion. But
							// detach from screen directly. Name screen 
							// session "tlc".
							// (see http://stackoverflow.com/a/10126799)
							+ "screen -dm -S tlc bash -c \" "
							// This requires a modified version where all parameters and
							// all spec modules are stored in files in a model/ folder
							// inside of the jar.
							// This is done in anticipation of other cloud providers
							// where one cannot easily pass in parameters on the command
							// line because there is no command line.
							+ "java "
								+ params.getJavaVMArgs() + " "
								// These properties cannot be "backed" into
								// the payload jar as java itself does not 
							    // support this.
								// It might be able to read the properties from 
								// the config file with 'com.sun.management.config.file=path',
								// but I haven't tried if the path can point into the jar.
								+ "-Dcom.sun.management.jmxremote "
								+ "-Dcom.sun.management.jmxremote.port=5400 "
								+ "-Dcom.sun.management.jmxremote.ssl=false "
								+ "-Dcom.sun.management.jmxremote.authenticate=false "
								// TLC tuning options
								+ params.getJavaSystemProperties() + " "
								+ "-jar /mnt/tlc/tla2tools.jar " 
								+ params.getTLCParameters() + " "
								+ "&& "
							// Let the machine power down immediately after
							// finishing model checking to cut costs. However,
							// do not shut down (hence "&&") when TLC finished
							// with an error.
							// It uses "sudo" because the script is explicitly
							// run as a user. No need to run the TLC process as
							// root.
							+ "sudo shutdown -h now"
							+ "\""), // closing opening '"' of screen/bash -c
					new TemplateOptions().runAsRoot(false).wrapInInitScript(
							true).blockOnComplete(false).blockUntilRunning(false));
			monitor.worked(5);

			// Communicate result to user
			monitor.done();
			final String hostname = Iterables.getOnlyElement(master.getPublicAddresses()); // master.getHostname() only returns internal name
			return new CloudStatus(
					Status.OK,
					"org.lamport.tla.toolbox.jcloud",
					Status.OK,
					String.format(
							"TLC is model checking at host %s. "
									+ "Expect to receive an email at %s with the model checking result eventually.",
							hostname,
							props.get("result.mail.address")), null, new URL(
							"http://" + hostname + "/munin/"));
		} catch (RunNodesException|IOException|RunScriptOnNodesException|NoSuchElementException|AuthorizationException e) {
			e.printStackTrace();
			if (context != null) {
				destroyNodes(context, groupNameUUID);
			}
			// signal error to caller
			return new Status(Status.ERROR, "org.lamport.tla.toolbox.jcloud",
					e.getMessage(), e);
		} finally {
			if (context != null) {
				// The user has canceled the Toolbox job, take this as a request
				// to destroy all nodes this job has created.
				if (monitor.isCanceled()) {
					destroyNodes(context, groupNameUUID);
				}
				context.close();
			}
		}
	}

	private static void destroyNodes(final ComputeServiceContext ctx, final String groupname) {
		// Destroy all workers identified by the given group
		final ComputeService computeService = ctx.getComputeService();
		if (computeService != null) {
			Set<? extends NodeMetadata> destroyed = computeService
					.destroyNodesMatching(
							Predicates.<NodeMetadata> and(not(TERMINATED),
									inGroup(groupname)));
			System.out.printf("<< destroyed nodes %s%n", destroyed);
		}
	}
	
	class CloudStatus extends Status implements ITLCJobStatus {

		private final URL url;

		public CloudStatus(int severity, String pluginId, int code,
				String message, Throwable exception, URL url) {
			super(severity, pluginId, code, message, exception);
			this.url = url;
		}

		@Override
		public URL getURL() {
			return url;
		}
	}
}
