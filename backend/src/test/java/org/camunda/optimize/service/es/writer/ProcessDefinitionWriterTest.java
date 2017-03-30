package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/unit/applicationContext.xml" })
public class ProcessDefinitionWriterTest {

  @Autowired
  private ProcessDefinitionWriter underTest;

  @Autowired
  private TransportClient transportClient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void importProcessDefinitions() throws Exception {
    //given
    IndexRequestBuilder indexMock = setupIndexRequestBuilder(configurationService.getProcessDefinitionType());
    ListenableActionFuture<BulkResponse> getMock = Mockito.mock(ListenableActionFuture.class);
    BulkRequestBuilder bulkRequest = setupBulkRequestBuilder(indexMock, getMock);

    List<ProcessDefinitionOptimizeDto> procDefs = new ArrayList<>();
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDefs.add(procDef);

    //when
    underTest.importProcessDefinitions(procDefs);

    //then
    Mockito.verify(indexMock, Mockito.times(1))
      .setSource(objectMapper.writeValueAsString(procDef));
    Mockito.verify(bulkRequest, Mockito.times(1)).execute();
    Mockito.verify(getMock, Mockito.times(1)).get();
  }

  @Test
  public void importProcessDefinitionXml() throws Exception {
    //given
    IndexRequestBuilder indexMock = setupIndexRequestBuilder(configurationService.getProcessDefinitionXmlType());
    ListenableActionFuture<BulkResponse> getMock = Mockito.mock(ListenableActionFuture.class);
    BulkRequestBuilder bulkRequest = setupBulkRequestBuilder(indexMock, getMock);

    List<ProcessDefinitionXmlOptimizeDto> xmls = new ArrayList<>();
    ProcessDefinitionXmlOptimizeDto procDefXml = new ProcessDefinitionXmlOptimizeDto();
    procDefXml.setId("123");
    procDefXml.setBpmn20Xml("TestBpmnXml");
    xmls.add(procDefXml);

    //when
    underTest.importProcessDefinitionXmls(xmls);

    //then
    Mockito.verify(indexMock, Mockito.times(1))
      .setSource(objectMapper.writeValueAsString(procDefXml));
    Mockito.verify(bulkRequest, Mockito.times(1)).execute();
    Mockito.verify(getMock, Mockito.times(1)).get();
  }

  private BulkRequestBuilder setupBulkRequestBuilder(IndexRequestBuilder indexMock, ListenableActionFuture<BulkResponse> getMock) {
    BulkRequestBuilder bulkRequest = Mockito.mock(BulkRequestBuilder.class);
    Mockito.when(transportClient.prepareBulk()).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.add(indexMock)).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.execute()).thenReturn(getMock);
    return bulkRequest;
  }

  private IndexRequestBuilder setupIndexRequestBuilder(String type) {
    IndexRequestBuilder indexMock = Mockito.mock(IndexRequestBuilder.class);
    Mockito.when(indexMock.setSource(Mockito.anyString())).thenReturn(indexMock);
    Mockito.when(transportClient.prepareIndex(
      Mockito.eq(configurationService.getOptimizeIndex()),
      Mockito.eq(type),
      Mockito.eq("123"))
    )
      .thenReturn(indexMock);
    return indexMock;
  }


}