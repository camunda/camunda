/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.zeebeimport.util.XMLUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XXEPreventionTest {

  @Test
  public void testCreateSecureSAXParser() throws Exception {
    final URL xxeFileUrl = XXEPreventionTest.class.getClassLoader().getResource("xxe-test.txt");
    final String xxeAttack =
        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
            + "<!DOCTYPE foo ["
            + "  <!ELEMENT foo ANY>"
            + "  <!ENTITY xxe SYSTEM \""
            + xxeFileUrl
            + "\">"
            + "]>"
            + "<foo>&xxe;</foo>";
    final InputStream xmlInputStream =
        new ByteArrayInputStream(xxeAttack.getBytes(StandardCharsets.UTF_8));
    final StringBuilder elementContent = new StringBuilder();

    new XMLUtil()
        .getSAXParserFactory()
        .newSAXParser()
        .parse(
            xmlInputStream,
            new DefaultHandler() {
              @Override
              public void characters(final char[] ch, final int start, final int length)
                  throws SAXException {
                elementContent.append(new String(ch, start, length));
              }
            });
    // the file must not be read
    assertThat(elementContent.toString()).isEqualTo("");
  }
}
