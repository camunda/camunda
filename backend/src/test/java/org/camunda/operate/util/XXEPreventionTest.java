package org.camunda.operate.util;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.camunda.operate.zeebeimport.processors.WorkflowZeebeRecordProcessor;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XXEPreventionTest extends DefaultHandler{

	private InputStream xmlInputStream;
	private StringBuilder elementContent;
	
	@Before
	public void setUp() throws Exception {
		String xxeFileUrl = new File("./src/test/resources/xxe-test.txt").toURI().toURL().toString();
		String XXE_ATTACK = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + 
		                    "<!DOCTYPE foo [" + 
		                    "  <!ELEMENT foo ANY>" + 
		                    "  <!ENTITY xxe SYSTEM \""+xxeFileUrl+"\">"+
		                    "]>" + 
		                    "<foo>&xxe;</foo>";
		xmlInputStream = new ByteArrayInputStream(XXE_ATTACK.getBytes(StandardCharsets.UTF_8));
		elementContent = new StringBuilder();
	}
	
	public SAXParser buildOperateSAXParser() {
		return new WorkflowZeebeRecordProcessor().getSAXParser();
	}
	
	public SAXParser buildSecureSAXParser() throws Exception {
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
	    saxParserFactory.setNamespaceAware(true);
	    saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities",false);
	    saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
	    return saxParserFactory.newSAXParser();
	}

	@Test
	public void testCreateOperateSAXParser() throws Exception {
	    buildOperateSAXParser().parse(xmlInputStream,this);
	    assertEquals("", elementContent.toString());
	}
	
	@Test
	public void testCreateSecureSAXParser() throws Exception {
	    buildSecureSAXParser().parse(xmlInputStream,this);
	    assertEquals("", elementContent.toString());
	}
	    
	@Override
    public void characters(char ch[], int start, int length) throws SAXException {
	    elementContent.append(new String(ch, start, length));
    }

}
