package liveu.tvbroadcast;

import java.util.LinkedList;

/**
 * limit sized queue - old elements over limit will be removed from queue 
 */

public class LimitedQueue<E> extends LinkedList<E> {
    private int limit;

    public LimitedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public synchronized boolean add(E o) {
        super.add(o);
        while (size() > limit) { super.remove(); }
        return true;
    }
}

