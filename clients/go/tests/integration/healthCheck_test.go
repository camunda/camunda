package integration_test

import (
	"github.com/zeebe-io/zeebe/clients/go"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"

	"log"

	"github.com/zeebe-io/zeebe/clients/go/pb"
)

var _ = Describe("should send HealthRequest to Gateway and receive HealthResponse", func() {

	BeforeSuite(func() {
		log.Print("starting broker")
		startBroker()
	})

	AfterSuite(func() {
		log.Println("killing broker")
		stopBroker()
	})

	var client zbc.ZBClient
	BeforeEach(func() {
		c, e := zbc.NewZBClient("0.0.0.0:26500")
		Expect(e).To(BeNil())
		Expect(c).NotTo(BeNil())
		client = c
	})

	Describe("should receive valid response", func() {
		Context("health check", func() {
			It("request with correct response", func() {
				response, err := client.HealthCheck()

				Expect(len(response.GetBrokers())).To(Equal(1))
				Expect(response.Brokers[0].Host).To(Equal("0.0.0.0"))
				Expect(response.Brokers[0].Port).To(Equal(int32(26501)))

				Expect(len(response.Brokers[0].Partitions)).To(Equal(1))

				Expect(response.Brokers[0].Partitions[0].PartitionId).To(Equal(int32(0)))
				Expect(response.Brokers[0].Partitions[0].Role).To(Equal(pb.Partition_LEADER))

				Expect(err).To(BeNil())
				Expect(response).NotTo(BeNil())
			})

			It("should timeout", func() {
				stopBroker()

				response, err := client.HealthCheck()
				Expect(response).To(BeNil())
				Expect(err).NotTo(BeNil())
			})
		})
	})

})
