package com.controlyourway;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by alangley on 16/10/15.
 */
public class JLinq {
    public static <T> T first(Collection<T> coll) {
        Iterator<T> iter = coll.iterator();
        if (!iter.hasNext())
            throw new IndexOutOfBoundsException("Sequence was empty.");

        return iter.next();
    }

    public static <T> Collection<T> where(Collection<T> coll, Action<T> chk) {
        LinkedList<T> l = new LinkedList<T>();
        for (T obj : coll) {
            if (chk.execute(obj))
                l.add(obj);
        }
        return l;
    }

    public interface Action<T> {
        boolean execute(T s);
    }
}
