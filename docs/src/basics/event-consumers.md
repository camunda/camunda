# Event Consumers



As Zeebe processes Tasks and Workflows, it generates a Stream of Events.

\[TODO: example\]

An important concept is the _position_ of a subscription. When opening a subscription to a topic, a consumer can choose to open the subscription at the head or tail of the topic or anywhere in between. Opening the subscription at the head allows you to process all past events up to the present and then the future. Opening the subscription at the tail will deliver all future events from the point in time the subscription was opened. The broker manages the positions of the subscriptions allowing clients to disconnect and go offline. When the client reconnects, it will continue processing at the position it has last acknowledged.

