package hcm.logue.feedback.classes;

import hcm.logue.feedback.events.Event;

/**
 * Created by Johnny on 03.06.2016.
 */
public interface FeedbackListener
{
    void onPostFeedback(hcm.ssj.core.event.Event ssjEvent, Event event, float value);
}
