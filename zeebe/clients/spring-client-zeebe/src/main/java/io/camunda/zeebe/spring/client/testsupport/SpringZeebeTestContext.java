package io.camunda.zeebe.spring.client.testsupport;

/**
 * Marker bean, if present in Spring context we are running in a test environment and might want to
 * adjustcertain lifecycle handlings
 */
public class SpringZeebeTestContext {}
