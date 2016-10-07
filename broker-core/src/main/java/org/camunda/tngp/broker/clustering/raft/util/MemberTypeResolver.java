package org.camunda.tngp.broker.clustering.raft.util;

import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.clustering.raft.MemberType;

public class MemberTypeResolver
{
    public static Member.Type getType(final MemberType memberType)
    {
        switch (memberType)
        {
            case INACTIVE:
            {
                return Member.Type.INACTIVE;
            }
            default:
            {
                return Member.Type.ACTIVE;
            }
        }
    }

    public static MemberType getMemberType(final Member.Type type)
    {
        switch (type)
        {
            case INACTIVE:
            {
                return MemberType.INACTIVE;
            }
            default:
            {
                return MemberType.ACTIVE;
            }
        }
    }

}
