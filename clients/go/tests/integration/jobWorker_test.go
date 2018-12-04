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

package integration

import (
	"fmt"
	"time"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/worker"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

var _ = Describe("JobWorker", func() {

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

	Context("job worker", func() {

		It("should create jobs and fail/complete with job worker", func() {
			var retries int32 = 2
			jobStartCount := 10
			jobs := jobStartCount

			_, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/simple-process.bpmn").Send()
			Expect(err).To(BeNil())

			for i := 0; i < jobs; i++ {
				go func() {
					_, err := client.
						NewCreateInstanceCommand().
						BPMNProcessId("simpleProcess").
						LatestVersion().
						Send()
					Expect(err).To(BeNil())
				}()
			}

			completeCallback := make(chan int64, jobs)

			jobWorker := client.NewJobWorker().JobType("foo").Handler(func(jobClient worker.JobClient, job entities.Job) {
				jobKey := job.Key
				if job.Retries < retries {
					_, err := jobClient.NewCompleteJobCommand().JobKey(jobKey).Send()
					Expect(err).To(BeNil())
					completeCallback <- jobKey
				} else {
					_, err := jobClient.NewFailJobCommand().JobKey(jobKey).Retries(retries - 1).Send()
					Expect(err).To(BeNil())
				}
			}).Name("goWorker").Timeout(5 * time.Second).PollInterval(1 * time.Second).Concurrency(4).BufferSize(32).Open()

			for {
				select {
				case <-completeCallback:
					jobs--
				case <-time.After(10 * time.Second):
					Fail(fmt.Sprintf("Expected to complete %d jobs, but %d left.", jobStartCount, jobs))
				}

				if jobs <= 0 {
					break
				}
			}

			jobWorker.Close()
		})
	})
})
