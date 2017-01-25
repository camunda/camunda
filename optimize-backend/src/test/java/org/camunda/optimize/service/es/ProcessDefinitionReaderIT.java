package org.camunda.optimize.service.es;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class ProcessDefinitionReaderIT {

  @Autowired
  private ProcessDefinitionReader procDefReader;

  @Test
  public void getProcessDefinitions() throws Exception {


    List<ProcessDefinitionDto> testDefinition = procDefReader.getProcessDefinitions();
    assertThat(testDefinition.size(), is(1));
    assertThat(testDefinition.get(0).getId(), is("123"));
    assertThat(testDefinition.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionXml() throws Exception {

    String testXml = procDefReader.getProcessDefinitionXmls("123");
    assertThat(testXml, is("testBpmnXml"));
  }

}