/*
 * MIT License
 *
 * Copyright (c) 2020 Donato Rimenti
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package co.aurasphere.metrics.jdk5;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

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
	private MetricRegistry registry = new MetricRegistry();

	/**
	 * JMX reporter for the metrics.
	 */
	private JmxReporter reporter = JmxReporter.forRegistry(registry).build();

	/**
	 * List of paths included OR excluded (not both) by this filter.
	 */
	private List<String> managedContexts = new ArrayList<String>();

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
		String defaultIncludeParameter = filterConfig.getInitParameter(WHITELIST_MODE_KEY);
		if (defaultIncludeParameter != null) {
			whitelistMode = Boolean.valueOf(defaultIncludeParameter);
			LOG.debug("Loaded [{}] value for parameter [{}]", whitelistMode, WHITELIST_MODE_KEY);
		}
		String managedPathsParameter = filterConfig.getInitParameter(MANAGED_CONTEXTS_KEY);
		if (managedPathsParameter != null) {
			// Splits the context paths.
			LOG.trace("Splitting and trimming managed paths.");
			String[] splittedPaths = managedPathsParameter.split(",");
			for (String path : splittedPaths) {
				LOG.trace("Trimming and adding path [{}]", path);
				managedContexts.add(path.trim());
			}
			LOG.debug("Loaded [{}] value for parameter [{}]", managedPathsParameter, MANAGED_CONTEXTS_KEY);
		}

		// Starts the JMX reporter.
		reporter.start();
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
			String requestUri = request.getRequestURI();
			LOG.debug("Condition is true, filtering with metrics for context URI [{}].", requestUri);

			// Starts the timer.
			long startTime = System.currentTimeMillis();
			try {
				// Executes the call and registers the times.
				chain.doFilter(request, response);
			} finally {
				registry.histogram(MetricRegistry.name(JmxMetricsFilter.class, requestUri, "responseCode",
						String.valueOf(response.getStatus()))).update(System.currentTimeMillis() - startTime);
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
		// Stops the reporter.
		LOG.trace("Stopping JMX reporter.");
		reporter.stop();
	}

}