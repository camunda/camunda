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
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
	"time"
)

type MessagePayload struct {
	ID string
}

func (msg MessagePayload) String() string {
	return fmt.Sprintf("{\"a\": \"%s\"}", msg.ID)
}

type MessagePayloadWithError struct {
	ID string
}

func (msg MessagePayloadWithError) String() string {
	return fmt.Sprintf("not valid json")
}

var _ = Describe("PublishMessage", func() {

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

	Context("publish messages", func() {
		It("should publish one message", func() {
			response, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("theId").
				Send()
			Expect(err).To(BeNil())
			Expect(response).NotTo(BeNil())
		})

		It("should publish multiple messages", func() {
			for i := 0; i < 10; i++ {
				response, err := client.
					NewPublishMessageCommand().
					MessageName(fmt.Sprintf("name_%d", i)).
					CorrelationKey(fmt.Sprintf("key_%d", i)).
					TimeToLive(time.Duration(24 * time.Hour)).
					MessageId(fmt.Sprintf("theId_%d", i)).
					Send()
				Expect(err).To(BeNil())
				Expect(response).NotTo(BeNil())
			}
		})

		It("should publish messages and error out", func() {
			_, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("theId").
				Send()
			Expect(err.Error()).NotTo(BeNil())
		})

		It("should publish a message with payload", func() {
			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId").
				PayloadFromString("{}")
			Expect(err).To(BeNil())
			Expect(cmd.Send()).NotTo(BeNil())

		})

		It("should publish a message with payload from string and error out", func() {
			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId2").
				PayloadFromString("not valid json")
			Expect(err).NotTo(BeNil())
			Expect(cmd).To(BeNil())
		})

		It("should publish a message with payload from string", func() {
			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId2").
				PayloadFromString("{\"something\": \"1234\"}")
			Expect(err).To(BeNil())
			Expect(cmd).To(Not(BeNil()))
			Expect(cmd.Send()).NotTo(BeNil())
		})

		It("should publish a message with payload from stringer and error out", func() {
			payload := MessagePayloadWithError{ID: "bla"}

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherID10").
				PayloadFromStringer(payload)

			Expect(err).NotTo(BeNil())
			Expect(cmd).To(BeNil())
		})

		It("should publish a message with payload from stringer", func() {
			payload := MessagePayload{ID: "bla"}

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId3").
				PayloadFromStringer(payload)

			Expect(err).To(BeNil())
			Expect(cmd).To(Not(BeNil()))
			Expect(cmd.Send()).NotTo(BeNil())
		})

		It("should publish a message with payload from object and error out", func() {
			payload := make(chan int)

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherID10").
				PayloadFromObject(payload)

			Expect(err.Error()).To(ContainSubstring("json: unsupported type: chan int"))
			Expect(cmd).To(BeNil())
		})

		It("should publish a message with payload from object", func() {
			payload := MessagePayload{ID: "bla"}

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId11").
				PayloadFromObject(payload)

			Expect(err).To(BeNil())
			Expect(cmd).To(Not(BeNil()))
			Expect(cmd.Send()).NotTo(BeNil())
		})

		It("should publish a message with payload from map and error out", func() {
			payload := make(map[string]interface{})
			payload["channel"] = make(chan int)

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherID20").
				PayloadFromMap(payload)

			Expect(err.Error()).To(ContainSubstring("json: unsupported type: chan int"))
			Expect(cmd).To(BeNil())
		})

		It("should publish a message with payload from map", func() {
			payload := make(map[string]interface{})
			payload["id"] = "something"

			cmd, err := client.
				NewPublishMessageCommand().
				MessageName("name").
				CorrelationKey("key").
				TimeToLive(time.Duration(24 * time.Hour)).
				MessageId("anotherId21").
				PayloadFromMap(payload)

			Expect(err).To(BeNil())
			Expect(cmd).To(Not(BeNil()))
			Expect(cmd.Send()).NotTo(BeNil())
		})

	})
})
