// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package integration_test

import (
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/pb"

	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

var _ = Describe("should send TopologyRequest to Gateway and receive TopologyResponse", func() {
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

	Context("topology", func() {
		It("request with correct response", func() {
			response, err := client.NewTopologyCommand().Send()

			Expect(response.ClusterSize).To(Equal(int32(1)))
			Expect(response.PartitionsCount).To(Equal(int32(1)))
			Expect(response.ReplicationFactor).To(Equal(int32(1)))

			Expect(len(response.Brokers)).To(Equal(1))
			Expect(len(response.Brokers[0].Partitions)).To(Equal(1))
			Expect(response.Brokers[0].Partitions[0].PartitionId).To(Equal(int32(1)))
			Expect(response.Brokers[0].Partitions[0].Role).To(Equal(pb.Partition_LEADER))

			Expect(err).To(BeNil())
			Expect(response).NotTo(BeNil())
		})
	})

})
