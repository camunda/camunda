package org.mockito.configuration;

import io.zeebe.test.util.TngpMockitoAnnotationEngine;

public class MockitoConfiguration extends DefaultMockitoConfiguration
{

    @Override
    public AnnotationEngine getAnnotationEngine()
    {
        return new TngpMockitoAnnotationEngine();
    }
}
