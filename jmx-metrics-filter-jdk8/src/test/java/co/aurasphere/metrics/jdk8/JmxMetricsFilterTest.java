package co.aurasphere.metrics.jdk8;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Test for {@link JmxMetricsFilter}.
 * 
 * @author Donato Rimenti
 */
public class JmxMetricsFilterTest {

	/**
	 * Mock servlet context for HTTP requests.
	 */
	private static final String MOCK_SERVLET_CONTEXT = "/myApp";

	/**
	 * Mock request URI HTTP requests.
	 */
	private static final String MOCK_REQUEST_URI = MOCK_SERVLET_CONTEXT + "/users";

	/**
	 * Object to test.
	 */
	private JmxMetricsFilter filter;

	/**
	 * Mock name of the exposed JMX bean.
	 */
	private static ObjectName jmxObjectName;

	/**
	 * Connector to the JMX.
	 */
	private static JMXConnector jmxConnector;

	/**
	 * Connector server to the JMX.
	 */
	private static JMXConnectorServer jmxConnectorServer;

	/**
	 * Connection to the MBean server.
	 */
	private static MBeanServerConnection mBeanServerConnection;

	/**
	 * Global setup which starts the JMX server used to check that the beans are
	 * correctly exposed.
	 * 
	 * @throws Exception if any error occurs during the JMX startup
	 */
	@BeforeClass
	public static void globalSetup() throws Exception {
		jmxConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(
				new JMXServiceURL("service:jmx:rmi://localhost:10124"), null,
				ManagementFactory.getPlatformMBeanServer());
		jmxConnectorServer.start();
		jmxConnector = JMXConnectorFactory.connect(jmxConnectorServer.getAddress());
		mBeanServerConnection = jmxConnector.getMBeanServerConnection();

		// Saves the name of the JMX exposed bean.
		jmxObjectName = new ObjectName("metrics:name=" + MOCK_REQUEST_URI + ".responseCode.0");
	}

	/**
	 * Global teardown which stops the JMX server.
	 * 
	 * @throws Exception if any error occurs during the JMX shutdown
	 */
	@AfterClass
	public static void globalTeardown() throws Exception {
		jmxConnector.close();
		jmxConnectorServer.stop();
	}

	/**
	 * Setup method which creates a new {@link #filter} to test.
	 */
	@Before
	public void setup() {
		filter = new JmxMetricsFilter();
	}

	/**
	 * Teardown method which cleans up the JMX state.
	 * 
	 * @throws Exception if any error occurs during the bean unregistration
	 */
	@After
	public void teardown() throws Exception {
		if (mBeanServerConnection.isRegistered(jmxObjectName)) {
			mBeanServerConnection.unregisterMBean(jmxObjectName);
		}
	}

	/**
	 * Tests {@link JmxMetricsFilter#init(FilterConfig)} with an empty
	 * configuration.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testInitWithEmptyConfig() {
		FilterConfig mockConfig = Mockito.mock(FilterConfig.class);
		filter.init(mockConfig);

		// Checks the filter state.
		List<String> managedContexts = (List<String>) Whitebox.getInternalState(filter, "managedContexts");
		Assert.assertTrue(managedContexts.isEmpty());
		Boolean whitelistMode = (Boolean) Whitebox.getInternalState(filter, "whitelistMode");
		Assert.assertTrue(whitelistMode);
	}

	/**
	 * Tests {@link JmxMetricsFilter#init(FilterConfig)} with a valid configuration.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testInitWithConfig() {
		FilterConfig mockConfig = Mockito.mock(FilterConfig.class);
		Mockito.when(mockConfig.getInitParameter("contexts")).thenReturn(MOCK_SERVLET_CONTEXT + ", /test-context-2");
		Mockito.when(mockConfig.getInitParameter("whitelist")).thenReturn("false");
		filter.init(mockConfig);

		// Checks the filter state.
		List<String> managedContexts = (List<String>) Whitebox.getInternalState(filter, "managedContexts");
		Assert.assertEquals(Arrays.asList(MOCK_SERVLET_CONTEXT, "/test-context-2"), managedContexts);
		Boolean whitelistMode = (Boolean) Whitebox.getInternalState(filter, "whitelistMode");
		Assert.assertFalse(whitelistMode);
	}

	/**
	 * Tests
	 * {@link JmxMetricsFilter#doFilter(javax.servlet.ServletRequest, ServletResponse, FilterChain)}
	 * in whitelist mode on a managed context.
	 * 
	 * @throws Exception if any error occurs during the method invocation or the
	 *                   result check on the JMX
	 */
	@Test
	public void testDoFilterWithWhitelistAndManagedContext() throws Exception {
		Whitebox.setInternalState(filter, "managedContexts", Arrays.asList(MOCK_SERVLET_CONTEXT));
		mockInvokeDoFilterAndCheckBeanRegistered(true);
	}

	/**
	 * Tests
	 * {@link JmxMetricsFilter#doFilter(javax.servlet.ServletRequest, ServletResponse, FilterChain)}
	 * in blacklist mode on a managed context.
	 * 
	 * @throws Exception if any error occurs during the method invocation or the
	 *                   result check on the JMX
	 */
	@Test
	public void testDoFilterWithBlacklistAndManagedContext() throws Exception {
		Whitebox.setInternalState(filter, "managedContexts", Arrays.asList(MOCK_SERVLET_CONTEXT));
		Whitebox.setInternalState(filter, "whitelistMode", false);
		mockInvokeDoFilterAndCheckBeanRegistered(false);
	}

	/**
	 * Tests
	 * {@link JmxMetricsFilter#doFilter(javax.servlet.ServletRequest, ServletResponse, FilterChain)}
	 * in whitelist mode on a context which is not managed.
	 * 
	 * @throws Exception if any error occurs during the method invocation or the
	 *                   result check on the JMX
	 */
	@Test
	public void testDoFilterWithWhitelistAndUnmanagedContext() throws Exception {
		Whitebox.setInternalState(filter, "managedContexts", Arrays.asList("/fake-path"));
		mockInvokeDoFilterAndCheckBeanRegistered(false);
	}

	/**
	 * Tests
	 * {@link JmxMetricsFilter#doFilter(javax.servlet.ServletRequest, ServletResponse, FilterChain)}
	 * in blacklist mode on a context which is not managed.
	 * 
	 * @throws Exception if any error occurs during the method invocation or the
	 *                   result check on the JMX
	 */
	@Test
	public void testDoFilterWithBlacklistAndUnmanagedContext() throws Exception {
		Whitebox.setInternalState(filter, "managedContexts", Arrays.asList("/fake-path"));
		Whitebox.setInternalState(filter, "whitelistMode", false);
		mockInvokeDoFilterAndCheckBeanRegistered(true);
	}

	/**
	 * Invokes the
	 * {@link JmxMetricsFilter#doFilter(javax.servlet.ServletRequest, ServletResponse, FilterChain)}
	 * method with a mocked request and checks the results.
	 * 
	 * @param expectedBeanRegistered true if the metrics of the invocations are
	 *                               supposed to be registered, false otherwise
	 * @throws Exception if any error occurs during the method invocation or the
	 *                   result check on the JMX
	 */
	private void mockInvokeDoFilterAndCheckBeanRegistered(boolean expectedBeanRegistered) throws Exception {
		// Mocks the request.
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		ServletResponse response = Mockito.mock(HttpServletResponse.class);
		Mockito.when(request.getRequestURI()).thenReturn(MOCK_REQUEST_URI);
		Mockito.when(request.getContextPath()).thenReturn(MOCK_SERVLET_CONTEXT);
		FilterChain chain = Mockito.mock(FilterChain.class);

		// Performs the request.
		filter.doFilter(request, response, chain);

		// Checks that the JMX values are exposed.
		Assert.assertEquals(expectedBeanRegistered, mBeanServerConnection.isRegistered(jmxObjectName));
	}

	/**
	 * Test for {@link JmxMetricsFilter#destroy()}.
	 */
	@Test
	public void testDestroy() {
		filter.destroy();

		// Checks that the registry has been closed.
		MeterRegistry registry = (MeterRegistry) Whitebox.getInternalState(filter, "registry");
		Assert.assertTrue(registry.isClosed());
	}

}