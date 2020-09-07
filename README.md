[![Travis](https://img.shields.io/travis/aurasphere/jmx-metrics-filter.svg)](https://travis-ci.org/aurasphere/jmx-metrics-filter)
[![Maven Central](https://img.shields.io/maven-central/v/co.aurasphere.metrics/jmx-metrics-filter-jdk5.svg)](https://search.maven.org/artifact/co.aurasphere.metrics/jmx-metrics-filter-jdk5/1.0.0/jar)
[![Javadocs](http://javadoc.io/badge/co.aurasphere.metrics/jmx-metrics-filter-jdk5.svg)](http://javadoc.io/doc/co.aurasphere.metrics/jmx-metrics-filter-jdk5)
[![Maintainability](https://api.codeclimate.com/v1/badges/43d564cf9ee6e93d8391/maintainability)](https://codeclimate.com/github/aurasphere/jmx-metrics-filter/maintainability)
[![Test Coverage](https://api.codeclimate.com/v1/badges/43d564cf9ee6e93d8391/test_coverage)](https://codeclimate.com/github/aurasphere/jmx-metrics-filter/test_coverage)
[![Join the chat at https://gitter.im/jmx-metrics-filter/community](https://badges.gitter.im/jmx-metrics-filter/community.svg)](https://gitter.im/jmx-metrics-filter/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Donate](https://img.shields.io/badge/Donate-PayPal-orange.svg)](https://www.paypal.com/donate/?cmd=_donations&business=8UK2BZP2K8NSS)

# JMX Metrics Filter

Servlet filter implementation for extracting metrics and exposing them on JMX. The filter will expose a bean for each combination of endpoint/HTTP status code with the invocation time stats (average, minimum, maximum...). 

Two artifacts are provided:
 - **jmx-metrics-filter-jdk8**, backward compatibile with Java 8, based on [micrometer.io](http://micrometer.io)
 - **jmx-metrics-filter-jdk5**, backward compatibile with Java 5, based on [metrics.dropwizard.io](https://metrics.dropwizard.io)
 
## Usage
To import the library using Maven, add this to your `pom.xml` (changing the * with the version you want to use):
 
    <dependency>
        <groupId>co.aurasphere.metrics</groupId>
        <artifactId>jmx-metrics-filter-jdk*</artifactId>
        <version>1.0.0</version>
    </dependency>
 
### Web application
To use the filter in a web application, add it on your `web.xml`:
 
    <filter>
        <filter-name>metrics-filter</filter-name>
        <filter-class>co.aurasphere.metrics.jdk*.JmxMetricsFilter</filter-class>
    </filter>
    <filter-mapping>
        <filter-name>metrics-filter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
 
 ### Servlet container
 To use the filter in a Servlet container, you have to manually add the jar and their dependencies to the container classpath:
 
 | jmx-metrics-filter-jdk5 | jmx-metrics-filter-jdk8 |
|:-------------:|:-------------:|
| slf4j-api-1.7.26.jar | slf4j-api-1.7.26.jar |
| LatencyUtils-2.0.3.jar | LatencyUtils-2.0.3.jar |
| metrics-core-3.2.6.jar | metrics-core-4.0.3.jar |
| metrics-jmx-3.2.6.jar | metrics-jmx-4.0.3.jar |
| | micrometer-core-1.1.4.jar |
| | micrometer-registry-jmx-1.1.4.jar |
 
 Then, you have to register the filter on the container. Follows an example on Tomcat:
 
    <filter>
        <filter-name>metrics-filter</filter-name>
        <filter-class>co.aurasphere.metrics.jdk*.JmxMetricsFilter</filter-class>
        <init-param>
            <param-name>contexts</param-name>
            <param-value>/web-test,/web-test-2</param-value>
        </init-param>
        <init-param>
            <param-name>whitelist</param-name>
            <param-value>true</param-value>
        </init-param>
    </filter>
    <filter-mapping>
        <filter-name>metrics-filter</filter-name>
        <url-pattern>*.html</url-pattern>
        <url-pattern>*.do</url-pattern>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
    
When using the filter on a container, you can tweak it further with the following `init-param`:
 - **contexts**, a list of comma-separed of web application contexts intercepted by this filter. If not specified, the filter will intercept requests on every context.
 - **whitelist**, specifies whether or not the specified contexts should be the only ones intercepted (true) or the only ones excluded by the filter (false). Defaults to true if not specified.

 ### Spring Boot
 To use the filter in a Spring Boot application, simply register the filter as a bean:
 
    @Bean
    public Filter jmxMetricsFilter() {
        return new JmxMetricsFilter();
    }

<sub>Copyright (c) 2019 Donato Rimenti</sub>
