# Create a Workflow in Zeebe Modeler

_New to BPMN and want to learn more before moving forward? [This blog post](https://zeebe.io/blog/2018/08/bpmn-for-microservices-orchestration-a-primer-part-1/) helps to explain the standard and why it's a good fit for microservices orchestration._

_In case you're already familiar with BPMN and how to create a BPMN model in Zeebe Modeler, you can find the finished model that we create during the tutorial here: [Zeebe Getting Started Tutorial Workflow Model](getting-started/img/order-process.bpmn)._ 

_If you're using the finished model we provide rather than building your own, you can also move ahead to [section 3.3: Deploy a Workflow](/getting-started/deploy-a-workflow.html)._

Zeebe Modeler is a desktop modeling tool that allows you to build and configure workflow models using BPMN 2.0. In this section, we'll create a workflow model and get it ready to be deployed to Zeebe.

We'll create an e-commerce order process as our example, and we'll model a workflow that consists of:

*   Initiating a payment for an order
*   Receiving a payment confirmation message from an external system
*   Shipping the items in the order with or without insurance depending on order value

This is what your workflow model will look like when we're finished:

![Getting Started Workflow Model](/getting-started/img/tutorial-3.0-complete-workflow.png)

The payment task and shipping tasks are carried out by worker services that we'll connect to the workflow engine. The "Payment Received" message will be published to Zeebe by an external system, and Zeebe will then correlate the message to a workflow instance.

To get started

*   Open the Zeebe Modeler and create a new BPMN diagram.
*   Save the model as `order-process.bpmn` in the top level of the Zeebe broker directory that you just downloaded. As a reminder, this directory is called `zeebe-broker-0.17.0`

The first element in your model will be a Start Event, which should already be on the canvas when you open the Modeler.

It's a BPMN best practice to label all elements in our model, so:



*   Double-click on the Start Event
*   Label it "Order Placed" to signify that our process will be initiated whenever a customer places an order

Next, we need to add a Service Task:



*   Click on the Start Event and select the Service Task icon
*   Label the Service Task "Initiate Payment"




Next, we'll configure the "Initiate Payment" Service Task so that an external microservice can work on it:

*   Click on the "Initiate Payment" task
*   Expand the Properties panel on the right side of the screen if it's not already visible
*   In the _Type_ field in the Properties panel, enter `initiate-payment`

This is what you should see in your Modeler now.

![Initiate Payment Service Task](/getting-started/img/tutorial-3.1-initiate-payment-task.png)

This _Type_ field represents the _job type_ in Zeebe. A couple of concepts that are important to understand at this point:

*   A _job_ is simply a work item in a workflow that needs to be completed before a workflow instance can proceed to the next step. (_[See: Job Workers](https://docs.zeebe.io/basics/job-workers.html)_)
*   A _workflow instance_ is one running instance of a workflow model--in our case, an individual order to be fulfilled. (_[See: Workflows](https://docs.zeebe.io/basics/workflows.html)_)

For every workflow instance that arrives at the "Initiate Payment" Service Task, Zeebe will create a job with type `initiate-payment`. The external worker service responsible for payment processing--the so-called job worker--will poll Zeebe intermittently to ask if any jobs of type `initiate-payment` are available.

If a job is available for a given workflow instance, the worker will activate it, complete it, and notify Zeebe. Zeebe will then advance that workflow instance to the next step in the workflow.

Next, we'll add a Message Event to the workflow:


*   Click on the "Initiate Payment" task on the Modeler
*   Select the circular icon with an envelope in the middle
*   Double-click on the message event and label it "Payment Received"

![Message Event](/getting-started/img/tutorial-3.2-modeler-message-event.png)



We use message catch events in Zeebe when the workflow engine needs to receive a message from an external system before the workflow instance can advance. (_[See: Message Events](https://docs.zeebe.io/bpmn-workflows/message-events.html)_)

In the scenario we're modeling, we _initiate_ a payment with our Service Task, but we need to wait for some other external system to actually confirm that the payment was received. This confirmation comes in the form of a message that will be sent to Zeebe - asynchronously - by an external service.

Messages received by Zeebe need to be correlated to specific workflow instances. To make this possible, we have some more configuring to do:

*   Select the Message Event and make sure you're on the "General" tab of the Properties panel on the right side of the screen
*   In the Properties panel, click the `+` icon to create a new message. You'll now see two fields in the Modeler that we'll use to correlate a message to a specific workflow instance: Message Name and Subscription Correlation Key.
*   Let's give this message a self-explanatory name: `payment-received`.

![Add Message Name](/getting-started/img/tutorial-3.3-add-message-name.png)

When Zeebe receives a message, this name field lets us know _which message event in the workflow model_ the message is referring to.



But how do we know which _specific workflow instance_--that is, which customer order--a message refers to? That's where Subscription Correlation Key comes in. The Subscription Correlation Key is a unique ID present in both the workflow instance payload and the message sent to Zeebe.

We'll use `orderId` for our correlation key.

Go ahead and add `orderId` to the Subscription Correlation Key field.

When we create a workflow instance, we need to be sure to include `orderId` as a variable, and we also need to provide `orderId` as a correlation key when we send a message.

Here's what you should see in the Modeler:

![Message Correlation Key](/getting-started/img/tutorial-3.4-add-correlation-key.png)

Next, we'll add an Exclusive (XOR) Gateway to our workflow model. The Exclusive Gateway is used to make a data-based decision about which path a workflow instance should follow. In this case, we want to ship items _with_ insurance if total order value is greater than or equal to $100 and ship _without_ insurance.  

That means that when we create a workflow instance, we'll need to include order value as an instance variable. But we'll come to that later.

First, let's take the necessary steps to configure our workflow model to make this decision. To add the gateway:

*   Click on the Message Event you just created
*   Select the Gateway (diamond-shaped) symbol - the Exclusive Gateway is the default when you add a new gateway to a model
*   Double-click on the gateway and add a label "Order Value?" so that it's clear what we're using as our decision criteria

![Add Exclusive Gateway to Model](/getting-started/img/tutorial-3.5-add-xor-gateway.png)

![Label Exclusive Gateway in Model](/getting-started/img/tutorial-3.6-label-xor-gateway.png)

We'll add two outgoing Sequence Flows from this Exclusive Gateway that lead to two different Service Tasks. Each Sequence Flow will have a data-based condition that's evaluated in the context of the workflow instance payload.

Next, we need to:



*   Select the gateway and add a new Service Task to the model.
*   Label the task "Ship Without Insurance"
*   Set the Type to `ship-without-insurance`



![Add No Insurance Service Task](/getting-started/img/tutorial-3.7-no-insurance-task.png)


Whenever we use an Exclusive Gateway, we want to be sure to set a default flow, which in this case will be shipping without insurance:



*   Select the Sequence Flow you just created from the gateway to the "Ship Without Insurance" Service Task
*   Click on the wrench icon
*   Choose "Default Flow"

![Add No Insurance Service Task](/getting-started/img/tutorial-3.8-default-flow.png)

Now we're ready to add a _second_ outgoing Sequence Flow and Service Task from the gateway:



*   Select the gateway again
*   Add another Service Task to the model
*   Label it "Ship With Insurance"
*   Set the type to `ship-with-insurance`

Next, we'll set a condition expression in the Sequence Flow leading to this "Ship With Insurance" Service Task:



*   Click on the sequence flow and open the Properties panel
*   Input  `orderValue >= 100` in the "Condition expression" field in the Properties panel
*   Double-click on the sequence flow to add a label "`>$100"`

![Condition Expression](/getting-started/img/tutorial-3.9-condition-expression.png)

We're almost finished! To wrap things up, we'll:



*   Select the "Ship Without Insurance" task
*   Add another Exclusive Gateway to the model to merge the branches together again (a BPMN best practice in a model like this one).
*   Select the "Ship With Insurance" task
*   Add an outgoing sequence flow that connects to the second Exclusive Gateway you just created

The only BPMN element we need to add is an End Event:



*   Click on the second Exclusive Gateway
*   Add an End Event
*   Double-click on it to label it "Order Fulfilled"

![Condition Expression](/getting-started/img/tutorial-3.10-end-event.png)

Lastly, we'll change the process ID to something more descriptive than the default `Process_1` that you'll see in the Modeler:



*   Click onto a blank part of the canvas
*   Open the Properties panel
*   Change the Id to `order-process`

Here's what you should see in the Modeler after these last few updates:

![Update Process ID](/getting-started/img/tutorial-3.11-process-id.png)

That's all for our modeling step. Remember to save the file one more time to prepare to deploy the workflow to Zeebe, create workflow instances, and complete them.

[**Next Page: Deploy a Workflow >>**](getting-started/deploy-a-workflow.html)

[**<< Previous Page: Tutorial Setup**](getting-started/tutorial-setup.html)
