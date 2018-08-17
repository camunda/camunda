package integration_test

import (
    . "github.com/onsi/ginkgo"
    . "github.com/onsi/gomega"
    "github.com/zeebe-io/zeebe/clients/go"
)

var _ = Describe("DeployWorkflow", func() {

	var client zbc.ZBClient
	BeforeEach(func() {
		c, e := zbc.NewZBClient("0.0.0.0:26500")
		Expect(e).To(BeNil())
		Expect(c).NotTo(BeNil())
		client = c
	})

	AfterEach(func() {
		client.Close()
	})

	Context("deploy BPMN workflows", func() {
		It("deploy one bpmn workflow", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))
			Expect(response.GetWorkflows()[0].Version).To(Equal(int32(1)))
			Expect(response.GetWorkflows()[0].WorkflowKey).To(Equal(int64(1)))
		})

		It("deploy two bpmn and one yaml workflow", func() {
            ids := []string{"demoProcess", "anotherDemoProcess", "yaml-workflow"}

			paths := []string{
			    "../../../java/src/test/resources/workflows/demo-process.bpmn",
			    "../../../java/src/test/resources/workflows/another-demo-process.bpmn",
			    "../../../java/src/test/resources/workflows/simple-workflow.yaml",
            }

			cmd := client.NewDeployWorkflowCommand()
			for _, path := range paths {
			    cmd = cmd.AddResourceFile(path)
            }
			response, err := cmd.Send()

			Expect(err).To(BeNil())
			Expect(len(response.GetWorkflows())).To(Equal(3))

			for _, workflow := range response.GetWorkflows() {
              Expect(Contains(workflow.BpmnProcessId, ids)).To(Equal(true))
              Expect(Contains(workflow.ResourceName, paths)).To(Equal(true))
            }
		})

	})
})
