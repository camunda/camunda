package integration

import (
	"encoding/json"
	"fmt"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
	"github.com/zeebe-io/zeebe/clients/go/commands"
)

type MyJob struct {
	ID   string
	Name string
}

func (job MyJob) String() string {
	return fmt.Sprintf("{\"name\": \"%s\", \"id\": \"%s\"}", "myjob", job.ID)
}

var _ = Describe("CreateJob", func() {

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

	Context("create job", func() {
		It("should create one job", func() {
			request := client.
				NewCreateJobCommand().
				JobType("foo").
				Retries(12).
				AddCustomHeader("a", "b").
				AddCustomHeader("b", "c").(*commands.CreateJobCommand)

			Expect(request.GetRequest().Retries).To(Equal(int32(12)))
			Expect(request.GetRequest().JobType).To(Equal("foo"))

			Expect(len(request.GetRequest().CustomHeaders)).To(Equal(0))
			response, err := request.Send()
			Expect(len(request.GetRequest().CustomHeaders)).NotTo(Equal(0))

			Expect(err).To(BeNil())
			Expect(response).NotTo(BeNil())

			Expect(len(request.GetRequest().CustomHeaders)).To(Equal(17))
			Expect(response.Key).NotTo(Equal(0))
		})

		It("should create multiple jobs", func() {
			for i := 0; i < 10; i++ {
				request := client.
					NewCreateJobCommand().
					JobType("foo").
					Retries(12).
					AddCustomHeader("a", "b").
					AddCustomHeader("b", "c").(*commands.CreateJobCommand)

				Expect(request.GetRequest().Retries).To(Equal(int32(12)))
				Expect(request.GetRequest().JobType).To(Equal("foo"))

				Expect(len(request.GetRequest().CustomHeaders)).To(Equal(0))
				response, err := request.Send()
				Expect(len(request.GetRequest().CustomHeaders)).NotTo(Equal(0))

				Expect(err).To(BeNil())
				Expect(response).NotTo(BeNil())

				Expect(len(request.GetRequest().CustomHeaders)).To(Equal(17))
				Expect(response.Key).NotTo(Equal(0))
			}
		})

		It("should create job with payload from string", func() {
			request, err := client.
				NewCreateJobCommand().
				JobType("foo").
				Retries(12).
				AddCustomHeader("a", "b").
				AddCustomHeader("b", "c").
				PayloadFromString("{}")

			Expect(err).To(BeNil())
			Expect(request.(*commands.CreateJobCommand).GetRequest().Payload).To(Equal("{}"))
		})

		It("should create job with payload from stringer", func() {
			job := MyJob{"something", "something"}
			request, err := client.
				NewCreateJobCommand().
				JobType("foo").
				Retries(12).
				AddCustomHeader("a", "b").
				AddCustomHeader("b", "c").
				PayloadFromStringer(job)
			Expect(err).To(BeNil())
			Expect(request.(*commands.CreateJobCommand).GetRequest().Payload).To(Equal(fmt.Sprintf("{\"name\": \"%s\", \"id\": \"%s\"}", "myjob", job.ID)))
		})

		It("should create job with payload from object", func() {
			job := MyJob{"something", "something"}
			request, err := client.
				NewCreateJobCommand().
				JobType("foo").
				Retries(12).
				AddCustomHeader("a", "b").
				AddCustomHeader("b", "c").
				PayloadFromObject(job)
			Expect(err).To(BeNil())

			b, err := json.Marshal(job)
			Expect(err).To(BeNil())
			Expect(request.(*commands.CreateJobCommand).GetRequest().Payload).To(Equal(string(b)))
		})
	})
})
