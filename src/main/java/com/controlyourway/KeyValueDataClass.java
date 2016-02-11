package com.controlyourway;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alangley on 16/10/15.
 */
public class KeyValueDataClass
{
    public String key;
    public byte[] value;

    public KeyValueDataClass(String key, List value)
    {
        this.key = key;
        this.value = new byte[value.size()];
        for(int i = 0; i < value.size(); i++)
            this.value[i] = (byte)value.get(i);
    }

//    public KeyValueDataClass(String key, Byte[] value)
//    {
//        this.key = key;
//        this.value = new byte[value.length];
//        this.value = value;
//    }
}
