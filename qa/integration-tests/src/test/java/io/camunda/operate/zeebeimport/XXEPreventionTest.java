/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import static org.junit.Assert.assertEquals;

public class XXEPreventionTest {

  @Test
  public void testCreateSecureSAXParser() throws Exception {
    URL xxeFileUrl = XXEPreventionTest.class.getClassLoader().getResource("xxe-test.txt");
    String xxeAttack = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
      + "<!DOCTYPE foo ["
      + "  <!ELEMENT foo ANY>"
      + "  <!ENTITY xxe SYSTEM \"" + xxeFileUrl + "\">"
      + "]>"
      + "<foo>&xxe;</foo>";
    InputStream xmlInputStream = new ByteArrayInputStream(xxeAttack.getBytes(StandardCharsets.UTF_8));
    StringBuilder elementContent = new StringBuilder();
    
    new XMLUtil().getSAXParserFactory().newSAXParser().parse(xmlInputStream, new DefaultHandler() {
      @Override
      public void characters(char ch[], int start, int length) throws SAXException {
        elementContent.append(new String(ch, start, length));
      }
    });
    //the file must not be read
    assertEquals("", elementContent.toString());
  }

}
