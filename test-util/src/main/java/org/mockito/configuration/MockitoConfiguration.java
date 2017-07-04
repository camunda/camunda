package org.mockito.configuration;

import io.zeebe.test.util.ZeebeMockitoAnnotationEngine;

public class MockitoConfiguration extends DefaultMockitoConfiguration
{

    @Override
    public AnnotationEngine getAnnotationEngine()
    {
        return new ZeebeMockitoAnnotationEngine();
    }
}
