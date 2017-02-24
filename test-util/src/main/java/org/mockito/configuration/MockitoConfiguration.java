package org.mockito.configuration;

import org.camunda.tngp.test.util.TngpMockitoAnnotationEngine;

public class MockitoConfiguration extends DefaultMockitoConfiguration
{

    @Override
    public AnnotationEngine getAnnotationEngine()
    {
        return new TngpMockitoAnnotationEngine();
    }
}
