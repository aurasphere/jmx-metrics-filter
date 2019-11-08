package co.aurasphere.metrics.jdk8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

/**
 * Servlet filter for measuring metrics for a set of endpoints and exposing them
 * through JMX.
 * 
 * The filter manages sub-contexts through the init parameter
 * {@value #MANAGED_CONTEXTS_KEY} as a comma-separed list of values. The
 * contexts are used by default as a whitelist but may be used as a blacklist by
 * setting the init parameter {@value #WHITELIST_MODE_KEY} to false.
 *
 * @author Donato Rimenti
 */
public class JmxMetricsFilter implements Filter {

	/**
	 * Logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(JmxMetricsFilter.class);

	/**
	 * Configuration key for {@link #managedContexts}.
	 */
	private static final String MANAGED_CONTEXTS_KEY = "contexts";

	/**
	 * Configuration key for {@link #whitelistMode}.
	 */
	private static final String WHITELIST_MODE_KEY = "whitelist";

	/**
	 * Registry to which the metrics are published.
	 */
	private MeterRegistry registry = new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);

	/**
	 * List of paths included OR excluded (not both) by this filter.
	 */
	private List<String> managedContexts = new ArrayList<>();

	/**
	 * Filter behavior, which may be to include only the {@link #managedContexts} or
	 * the opposite. Defaults to the former.
	 */
	private boolean whitelistMode = true;

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) {
		LOG.debug("Running init on JmxMetricsFilter");

		// Gets the default behavior and the managed paths from the filter
		// configuration.
		LOG.trace("Loading configuration values from init parameters");
		String whitelistModeParameter = filterConfig.getInitParameter(WHITELIST_MODE_KEY);
		if (whitelistModeParameter != null) {
			whitelistMode = Boolean.valueOf(whitelistModeParameter);
			LOG.debug("Loaded [{}] value for parameter [{}]", whitelistMode, WHITELIST_MODE_KEY);
		}
		String managedPathsParameter = filterConfig.getInitParameter(MANAGED_CONTEXTS_KEY);
		if (managedPathsParameter != null) {
			// Splits the context paths.
			LOG.trace("Splitting and trimming managed paths.");
			String[] splittedPaths = managedPathsParameter.split(",");
			Stream.of(splittedPaths).map(String::trim).forEach(managedContexts::add);
			LOG.debug("Loaded [{}] value for parameter [{}]", managedPathsParameter, MANAGED_CONTEXTS_KEY);
		}

		LOG.debug("Initializazion complete");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 * javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		// Removes the context segment from the request path.
		HttpServletRequest requestHttp = (HttpServletRequest) request;
		HttpServletResponse responseHttp = (HttpServletResponse) response;
		String contextPath = requestHttp.getContextPath();
		String requestPath = requestHttp.getRequestURI().substring(contextPath.length());
		LOG.debug("Filtering request with context [{}] and path [{}]", contextPath, requestPath);

		// Checks if the current context matches against the managed ones.
		for (String path : managedContexts) {
			if (contextPath.equalsIgnoreCase(path)) {
				// If the behavior is "whitelist", then the supplied paths must be
				// included, otherwise excluded.
				LOG.debug("Path [{}] matched. Metrics computed: [{}]", requestPath, whitelistMode);
				conditionalMeasureFilter(requestHttp, responseHttp, chain, whitelistMode);

				// The pattern has matched and has been handled, no need to continue.
				return;
			}
		}

		// If here, the pattern has not been matched. In this case, we apply the
		// opposite behavior.
		LOG.debug("Path [{}] did not match. Metrics computed: [{}]", requestPath, !whitelistMode);
		conditionalMeasureFilter(requestHttp, responseHttp, chain, !whitelistMode);
	}

	/**
	 * Invokes {@link FilterChain#doFilter(ServletRequest, ServletResponse)} with or
	 * without measuring the response metrics, according to the condition parameter.
	 *
	 * @param request   the current request
	 * @param response  the current response
	 * @param chain     the current filter chain
	 * @param condition whether or not the metrics should be measured
	 * @throws IOException      if
	 *                          {@link FilterChain#doFilter(ServletRequest, ServletResponse)}
	 *                          throws it
	 * @throws ServletException if
	 *                          {@link FilterChain#doFilter(ServletRequest, ServletResponse)}
	 *                          throws it
	 */
	private void conditionalMeasureFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
			boolean condition) throws IOException, ServletException {
		// Computes metrics according to the condition parameter.
		if (condition) {
			// Extracts logging informations.
			String contextPath = request.getContextPath();
			String nestedPath = request.getRequestURI().substring(contextPath.length());
			LOG.debug("Condition is true, filtering with metrics for context [{}] and path [{}].", contextPath,
					nestedPath);

			// Starts the timer.
			long startTime = System.currentTimeMillis();
			try {
				// Executes the call and registers the times.
				chain.doFilter(request, response);
			} finally {
				// Registers the time.
				Timer timer = registry.timer(request.getRequestURI(), "responseCode",
						String.valueOf(response.getStatus()));
				timer.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
			}
		} else {
			LOG.debug("Condition is false, filtering without metrics.");
			chain.doFilter(request, response);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
		// Closes the registry.
		LOG.trace("Closing the registry.");
		registry.close();
	}

}