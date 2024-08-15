/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

// TODO rename
public class NewMultiInstanceTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(47);

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .zeebeOutputExpression(getProcessVariable(), "bill")
          .subProcess(
              "subprocess1",
              s ->
                  s.multiInstance(
                      b ->
                          b.zeebeInputCollectionExpression("bill.lines").zeebeInputElement("line")))
          .embeddedSubProcess()
          .startEvent("sub-process-start")
          .subProcess(
              "subprocess2",
              s ->
                  s.multiInstance(
                      b ->
                          b.zeebeInputCollectionExpression("line.orders")
                              .zeebeInputElement("order")))
          .embeddedSubProcess()
          .startEvent("subprocess2StartEvent")
          .exclusiveGateway("exclusive-gateway")
          .defaultFlow()
          .exclusiveGateway()
          .endEvent()
          .subProcessDone()
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void test() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final int orderCount = 50;
    final String startEventToLookFor = "subprocess2StartEvent";

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId(startEventToLookFor)
                .limit(orderCount)
                .toList())
        .hasSize(orderCount);
  }

  private static String getProcessVariable() {
    return """
           = {
             	"billId" : "1",
             	"name": "Facture 1",
             	"state": "Ok",
             	"lines": [
             		{
             			"lineId": "11",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "111",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "112",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "113",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "114",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "115",
             					"state": "Ko",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "12",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "121",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "122",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "123",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "124",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "125",
             					"state": "E2",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "13",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "131",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "132",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "133",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "134",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "135",
             					"state": "E1",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "14",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "141",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "142",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "143",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "144",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "145",
             					"state": "18",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "15",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "151",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "152",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "153",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "154",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "155",
             					"state": "Ko",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "16",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "161",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "162",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "163",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "164",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "165",
             					"state": "Ko",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "17",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "171",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "172",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "173",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "174",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "175",
             					"state": "Ko",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "18",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "181",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "182",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "183",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "184",
             					"state": "Ok",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "185",
             					"state": "Ko",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "19",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "191",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "192",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "193",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "194",
             					"state": "Ko",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "195",
             					"state": "Ok",
             					"name": "Commande PC5"
             				}
             			]
             		},
             		{
             			"lineId": "21",
             			"state": "Ok",
             			"orders": [
             				{
             					"orderId": "211",
             					"state": "Ok",
             					"name": "Commande PC1"
             				},
             				{
             					"orderId": "212",
             					"state": "Ok",
             					"name": "Commande PC2"
             				},
             				{
             					"orderId": "213",
             					"state": "Ok",
             					"name": "Commande PC3"
             				},
             				{
             					"orderId": "214",
             					"state": "Ko",
             					"name": "Commande PC4"
             				},
             				{
             					"orderId": "215",
             					"state": "Ok",
             					"name": "Commande PC5"
             				}
             			]
             		}
             	]
             }""";
  }
}
