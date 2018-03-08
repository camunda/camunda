package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.*;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

class RecordingActor extends Actor
{
    public static final List<ActorLifecyclePhase> FULL_LIFECYCLE = newArrayList(STARTING, STARTED, CLOSE_REQUESTED, CLOSING, CLOSED);

    public List<ActorLifecyclePhase> phases = new ArrayList<>();

    @Override
    public void onActorStarting()
    {
        phases.add(actor.getLifecyclePhase());
    }

    @Override
    public void onActorStarted()
    {
        phases.add(actor.getLifecyclePhase());
    }

    @Override
    public void onActorClosing()
    {
        phases.add(actor.getLifecyclePhase());
    }

    @Override
    public void onActorClosed()
    {
        phases.add(actor.getLifecyclePhase());
    }

    @Override
    public void onActorCloseRequested()
    {
        phases.add(actor.getLifecyclePhase());
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }

    protected void blockPhase()
    {
        blockPhase(new CompletableActorFuture<>());
    }

    @SuppressWarnings("unchecked")
    protected void blockPhase(ActorFuture<Void> future)
    {
        actor.runOnCompletion(future, mock(BiConsumer.class));
    }

    public ActorControl control()
    {
        return actor;
    }
}