/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.camunda.operate.zeebeimport.processors.WorkflowZeebeRecordProcessor;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XXEPreventionTest extends DefaultHandler {

  @Test
  public void testCreateSecureSAXParser() throws Exception {
    URL xxeFileUrl = XXEPreventionTest.class.getClassLoader().getResource("xxe-test.txt");
    String xxeAttack = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + "<!DOCTYPE foo [" + "  <!ELEMENT foo ANY>" + "  <!ENTITY xxe SYSTEM \"" + xxeFileUrl
        + "\">" + "]>" + "<foo>&xxe;</foo>";
    InputStream xmlInputStream = new ByteArrayInputStream(xxeAttack.getBytes(StandardCharsets.UTF_8));
    StringBuilder elementContent = new StringBuilder();
    
    new WorkflowZeebeRecordProcessor().getSAXParser().parse(xmlInputStream, new DefaultHandler() {
      @Override
      public void characters(char ch[], int start, int length) throws SAXException {
        elementContent.append(new String(ch, start, length));
      }
    });
    assertEquals("", elementContent.toString());
  }

}
