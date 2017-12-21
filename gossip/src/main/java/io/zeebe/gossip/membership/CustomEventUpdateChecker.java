package io.zeebe.gossip.membership;

import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.CustomEventConsumer;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;

public class CustomEventUpdateChecker implements CustomEventConsumer
{
    private final MembershipList membershipList;

    public CustomEventUpdateChecker(MembershipList membershipList)
    {
        this.membershipList = membershipList;
    }

    @Override
    public boolean consumeCustomEvent(CustomEvent event)
    {
        boolean isNew = false;

        final SocketAddress sender = event.getSenderAddress();
        final DirectBuffer eventType = event.getType();
        final GossipTerm senderGossipTerm = event.getSenderGossipTerm();

        if (membershipList.hasMember(sender))
        {
            final Member member = membershipList.get(sender);
            final GossipTerm currentTerm = member.getTermForEventType(eventType);

            if (currentTerm == null)
            {
                member.addTermForEventType(eventType, senderGossipTerm);

                isNew = true;
            }
            else if (senderGossipTerm.isGreaterThan(currentTerm))
            {
                currentTerm
                    .epoch(senderGossipTerm.getEpoch())
                    .heartbeat(senderGossipTerm.getHeartbeat());

                isNew = true;
            }
        }
        else
        {
            // ignore
        }

        return isNew;
    }

}
