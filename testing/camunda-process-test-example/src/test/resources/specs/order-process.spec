{
  "testResources": [],
  "testCases":[
    {
      "name":"happy path",
      "instructions":[
        {
          "name":"create-process-instance",
          "processId":"order-process",
          "variables":"{\"order_id\":\"order-1\"}",
          "processInstanceAlias":"process-instance"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementName": "Collect money",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementName": "Fetch items",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementName": "Ship parcel",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementName": "Received tracking code",
          "state":"active"
        },
        {
          "name":"publish-message",
          "messageName": "Received tracking code",
          "correlationKey": "shipping-1",
          "variables": "{\"tracking_code\":\"tracking-1\"}"
        },
        {
          "name":"verify-process-instance-state",
          "processInstanceAlias":"process-instance",
          "state":"completed"
        }
      ]
    }
  ]
}
