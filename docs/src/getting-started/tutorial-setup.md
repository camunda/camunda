# Tutorial Setup

Welcome to the Getting Started tutorial for Zeebe and Operate. In this tutorial, we'll walk you through how to...

*   Model a workflow using Zeebe Modeler
*   Deploy the workflow to Zeebe
*   Create workflow instances
*   Use workers to complete jobs created by those workflow instances
*   Correlate messages to workflow instances
*   Monitor what's happening and get detail about running workflow instances in Operate

If this is your first time working with Zeebe, we expect this entire guide to take you 30-45 minutes to complete.

If you're looking for a very fast (but less comprehensive) "first contact" experience, you might prefer the [Quickstart](https://docs.zeebe.io/introduction/quickstart.html).

The tutorial assumes you have some basic knowledge of what Zeebe is and what it's used for. If you're completely new to Zeebe, you might find it helpful to read through the ["What is Zeebe?"](https://docs.zeebe.io/introduction/what-is-zeebe.html) docs article first.

Below are the components you'll use in the tutorial. Please download the full distributions for these components instead of running them with Docker.

1.   [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases): A desktop modeling tool that we'll use to create and configure our workflow before we deploy it to Zeebe.
1.   [Zeebe Distribution](https://github.com/zeebe-io/zeebe/releases/tag/0.17.0): The Zeebe distribution contains the workflow engine where we'll deploy our workflow model; the engine is also responsible for managing the state of active workflow instances. Included in the distro is the Zeebe CLI, which we'll use throughout the tutorial. Please use Zeebe 0.17.0.
1.   [Camunda Operate](https://github.com/zeebe-io/zeebe/releases/tag/0.17.0): An operations tool for monitoring and troubleshooting live workflow instances in Zeebe. Operate is currently available for free and unrestricted _non-production use_.
1.   [Elasticsearch 6.7.0](https://www.elastic.co/downloads/past-releases/elasticsearch-6-7-0): An open-source distributed datastore that can connect to Zeebe to store workflow data for auditing, visualization, analysis, etc. Camunda Operate uses Elasticsearch as its underlying datastore, which is why you need to download Elasticsearch to complete this tutorial. Operate and Zeebe are compatible with Elasticsearch 6.7.0.

In case you're already familiar with BPMN and how to create a BPMN model in Zeebe Modeler, you can find the finished model that we create during the tutorial here: [Zeebe Getting Started Tutorial Workflow Model](getting-started/img/order-process.bpmn). 

If you're using the finished model we provide rather than building your own, you can also move ahead to [section 3.3: Deploy a Workflow](/getting-started/deploy-a-workflow.html).

And if you have questions or feedback about the tutorial, we encourage you to visit the [Zeebe user forum](https://forum.zeebe.io) and ask a question.

There's a "Getting Started" category for topics that you can use when you ask your question or give feedback.

[**Next Page: Create a Workflow >>**](getting-started/create-a-workflow.html)
