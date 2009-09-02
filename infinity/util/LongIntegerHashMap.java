// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.util;

import java.util.*;

public final class LongIntegerHashMap <V> implements Cloneable
{
  private static final float DEFAULT_LOAD_FACTOR = 0.75f;
  private static final int DEFAULT_INITIAL_CAPACITY = 16;
  private static final int MAXIMUM_CAPACITY = 1 << 30;

  private final float loadFactor;
  private Collection<V> values;
  private Entry<V>[] table;
  private Set<LongIntegerHashMap.Entry<V>> entrySet;
  private int size;
  private int threshold;
  private int modCount;
  private long keys[];

  private static int hash(long x)
  {
    int h = (int)(x ^ x >>> 32);
    h += ~(h << 9);
    h ^= h >>> 14;
    h += h << 4;
    h ^= h >>> 10;
    return h;
  }

  private static int indexFor(int h, int length)
  {
    return h & length - 1;
  }

  public LongIntegerHashMap()
  {
    loadFactor = DEFAULT_LOAD_FACTOR;
    threshold = (int)(DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR);
    table = new Entry[DEFAULT_INITIAL_CAPACITY];
  }

  public LongIntegerHashMap(int initialCapacity)
  {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
  }

  public LongIntegerHashMap(LongIntegerHashMap<? extends V> m)
  {
    this(Math.max((int)(m.size() / DEFAULT_LOAD_FACTOR) + 1,
                  DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
    putAllForCreate(m);
  }

  public LongIntegerHashMap(int initialCapacity, float loadFactor)
  {
    if (initialCapacity < 0)
      throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
    if (initialCapacity > MAXIMUM_CAPACITY)
      initialCapacity = MAXIMUM_CAPACITY;
    if (loadFactor <= 0 || Float.isNaN(loadFactor))
      throw new IllegalArgumentException("Illegal load factor: " + loadFactor);

    // Find a power of 2 >= initialCapacity
    int capacity = 1;
    while (capacity < initialCapacity)
      capacity <<= 1;

    this.loadFactor = loadFactor;
    threshold = (int)(capacity * loadFactor);
    table = new Entry[capacity];
  }

  public Object clone()
  {
    LongIntegerHashMap<V> result = null;
    try {
      result = (LongIntegerHashMap<V>)super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }
    result.keys = null;
    result.values = null;
    result.table = new Entry[table.length];
    result.entrySet = null;
    result.modCount = 0;
    result.size = 0;
    result.putAllForCreate(this);

    return result;
  }

  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    if (!(o instanceof LongIntegerHashMap))
      return false;
    LongIntegerHashMap<V> t = (LongIntegerHashMap<V>)o;
    if (t.size() != size())
      return false;
    try {
      Iterator<Entry<V>> i = entrySet().iterator();
      while (i.hasNext()) {
        Entry<V> e = i.next();
        long key = e.getKey();
        V value = e.getValue();
        if (value == null) {
          if (!(t.get(key) == null && t.containsKey(key)))
            return false;
        }
        else {
          if (!value.equals(t.get(key)))
            return false;
        }
      }
    } catch (ClassCastException unused) {
      return false;
    } catch (NullPointerException unused) {
      return false;
    }
    return true;
  }

  public int hashCode()
  {
    int h = 0;
    Iterator<Entry<V>> i = entrySet().iterator();
    while (i.hasNext())
      h += i.next().hashCode();
    return h;
  }

  public String toString()
  {
    StringBuffer buf = new StringBuffer();
    buf.append('{');
    Iterator<Entry<V>> i = entrySet().iterator();
    boolean hasNext = i.hasNext();
    while (hasNext) {
      Entry<V> e = i.next();
      long key = e.getKey();
      V value = e.getValue();
      buf.append(key);
      buf.append('=');
      if (value == this)
        buf.append("(this Map)");
      else
        buf.append(value);
      hasNext = i.hasNext();
      if (hasNext)
        buf.append(", ");
    }
    buf.append('}');
    return buf.toString();
  }

  public void clear()
  {
    modCount++;
    Arrays.fill(table, null);
    size = 0;
    keys = null;
  }

  public boolean containsKey(long key)
  {
    int hash = hash(key);
    int i = indexFor(hash, table.length);
    Entry e = table[i];
    while (e != null) {
      if (e.hash == hash && key == e.key)
        return true;
      e = e.next;
    }
    return false;
  }

  public boolean containsValue(Object value)
  {
    if (value == null)
      return containsNullValue();

    Entry[] tab = table;
    for (final Entry entry : tab)
      for (Entry e = entry; e != null; e = e.next)
        if (value.equals(e.value))
          return true;
    return false;
  }

  public Set<LongIntegerHashMap.Entry<V>> entrySet()
  {
    Set<LongIntegerHashMap.Entry<V>> es = entrySet;
    return es != null ? es : (entrySet = (Set<LongIntegerHashMap.Entry<V>>)new EntrySet());
  }

  public V get(long key)
  {
    int hash = hash(key);
    int i = indexFor(hash, table.length);
    Entry<V> e = table[i];
    while (true) {
      if (e == null)
        return null;
      if (e.hash == hash && key == e.key)
        return e.value;
      e = e.next;
    }
  }

  public boolean isEmpty()
  {
    return size == 0;
  }

  public long[] keys()
  {
    if (keys == null) {
      keys = new long[size];
      int index = 0;
      for (final Entry<V> entry : entrySet())
        keys[index++] = entry.getKey();
    }
    return keys;
  }

  public V put(long key, V value)
  {
    int hash = hash(key);
    int i = indexFor(hash, table.length);

    for (Entry<V> e = table[i]; e != null; e = e.next) {
      if (e.hash == hash && key == e.key) {
        V oldValue = e.value;
        e.value = value;
        return oldValue;
      }
    }
    modCount++;
    Entry<V> e1 = table[i];
    table[i] = new Entry<V>(hash, key, value, e1);
    if (size++ >= threshold)
      resize(2 * table.length);
    keys = null;
    return null;
  }

  public void putAll(LongIntegerHashMap<? extends V> m)
  {
    int numKeysToBeAdded = m.size();
    if (numKeysToBeAdded == 0)
      return;

    if (numKeysToBeAdded > threshold) {
      int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
      if (targetCapacity > MAXIMUM_CAPACITY)
        targetCapacity = MAXIMUM_CAPACITY;
      int newCapacity = table.length;
      while (newCapacity < targetCapacity)
        newCapacity <<= 1;
      if (newCapacity > table.length)
        resize(newCapacity);
    }

    for (Entry<? extends V> e : m.entrySet())
      put(e.getKey(), e.getValue());
  }

  public V remove(long key)
  {
    Entry<V> e = removeEntryForKey(key);
    return e == null ? null : e.value;
  }

  public int size()
  {
    return size;
  }

  public Collection<V> values()
  {
    Collection<V> vs = values;
    return vs != null ? vs : (values = new Values());
  }

  private boolean containsNullValue()
  {
    Entry[] tab = table;
    for (final Entry entry : tab)
      for (Entry e = entry; e != null; e = e.next)
        if (e.value == null)
          return true;
    return false;
  }

  private void putAllForCreate(LongIntegerHashMap<? extends V> m)
  {
    for (Entry<? extends V> e : m.entrySet())
      putForCreate(e.getKey(), e.getValue());
  }

  private void putForCreate(long key, V value)
  {
    int hash = hash(key);
    int i = indexFor(hash, table.length);

    for (Entry<V> e = table[i]; e != null; e = e.next) {
      if (e.hash == hash && key == e.key) {
        e.value = value;
        return;
      }
    }
    Entry<V> e1 = table[i];
    table[i] = new Entry<V>(hash, key, value, e1);
    size++;
    keys = null;
  }

  private Entry<V> removeEntryForKey(long key)
  {
    int hash = hash(key);
    int i = indexFor(hash, table.length);
    Entry<V> prev = table[i];
    Entry<V> e = prev;
    keys = null;
    while (e != null) {
      Entry<V> next = e.next;
      if (e.hash == hash && key == e.key) {
        modCount++;
        size--;
        if (prev == e)
          table[i] = next;
        else
          prev.next = next;
        return e;
      }
      prev = e;
      e = next;
    }

    return e;
  }

  private Entry<V> removeMapping(Object o)
  {
    if (!(o instanceof LongIntegerHashMap.Entry))
      return null;

    LongIntegerHashMap.Entry<V> entry = (LongIntegerHashMap.Entry<V>)o;
    int hash = hash(entry.getKey());
    int i = indexFor(hash, table.length);
    Entry<V> prev = table[i];
    Entry<V> e = prev;
    keys = null;
    while (e != null) {
      Entry<V> next = e.next;
      if (e.hash == hash && e.equals(entry)) {
        modCount++;
        size--;
        if (prev == e)
          table[i] = next;
        else
          prev.next = next;
        return e;
      }
      prev = e;
      e = next;
    }

    return e;
  }

  private void resize(int newCapacity)
  {
    Entry<V>[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity == MAXIMUM_CAPACITY) {
      threshold = Integer.MAX_VALUE;
      return;
    }
    keys = null;
    Entry<V>[] newTable = new Entry[newCapacity];
    Entry<V>[] src = table;
    int newCapacity1 = newTable.length;
    for (int j = 0; j < src.length; j++) {
      Entry<V> e = src[j];
      if (e != null) {
        src[j] = null;
        do {
          Entry<V> next = e.next;
          int i = indexFor(e.hash, newCapacity1);
          e.next = newTable[i];
          newTable[i] = e;
          e = next;
        } while (e != null);
      }
    }
    table = newTable;
    threshold = (int)(newCapacity * loadFactor);
  }

// -------------------------- INNER CLASSES --------------------------

  private static final class Entry <V>
  {
    final long key;
    V value;
    final int hash;
    Entry<V> next;

    private Entry(int h, long k, V v, Entry<V> n)
    {
      value = v;
      next = n;
      key = k;
      hash = h;
    }

    public long getKey()
    {
      return key;
    }

    public V getValue()
    {
      return value;
    }

    public V setValue(V newValue)
    {
      V oldValue = value;
      value = newValue;
      return oldValue;
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof LongIntegerHashMap.Entry))
        return false;
      LongIntegerHashMap.Entry e = (LongIntegerHashMap.Entry)o;
      long k1 = getKey();
      long k2 = e.getKey();
      if (k1 == k2) {
        Object v1 = getValue();
        Object v2 = e.getValue();
        if (v1 == v2 || v1 != null && v1.equals(v2))
          return true;
      }
      return false;
    }

    public int hashCode()
    {
      return (int)(key ^ key >>> 32) ^ (value == null ? 0 : value.hashCode());
    }

    public String toString()
    {
      return getKey() + "=" + getValue();
    }

  }

  private abstract class HashIterator <E> implements Iterator<E>
  {
    Entry<V> next;	// next entry to return
    int expectedModCount;	// For fast-fail
    int index;		// current slot
    Entry<V> current;	// current entry

    HashIterator()
    {
      expectedModCount = modCount;
      Entry<V>[] t = table;
      int i = t.length;
      Entry<V> n = null;
      if (size != 0) { // advance to first entry
        while (i > 0 && (n = t[--i]) == null)
          ;
      }
      next = n;
      index = i;
    }

    public boolean hasNext()
    {
      return next != null;
    }

    Entry<V> nextEntry()
    {
      if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
      Entry<V> e = next;
      if (e == null)
        throw new NoSuchElementException();

      Entry<V> n = e.next;
      Entry<V>[] t = table;
      int i = index;
      while (n == null && i > 0)
        n = t[--i];
      index = i;
      next = n;
      return current = e;
    }

    public void remove()
    {
      if (current == null)
        throw new IllegalStateException();
      if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
      long k = current.key;
      current = null;
      LongIntegerHashMap.this.removeEntryForKey(k);
      expectedModCount = modCount;
    }

  }

  private final class ValueIterator extends HashIterator<V>
  {
    public V next()
    {
      return nextEntry().value;
    }
  }

  private final class EntryIterator extends HashIterator<LongIntegerHashMap.Entry<V>>
  {
    public LongIntegerHashMap.Entry<V> next()
    {
      return nextEntry();
    }
  }

  private final class Values extends AbstractCollection<V>
  {
    public Iterator<V> iterator()
    {
      return new ValueIterator();
    }

    public int size()
    {
      return size;
    }

    public boolean contains(Object o)
    {
      return containsValue(o);
    }

    public void clear()
    {
      LongIntegerHashMap.this.clear();
    }
  }

  private final class EntrySet extends AbstractSet/*<Map.Entry<K,V>>*/
  {
    public Iterator/*<Map.Entry<K,V>>*/ iterator()
    {
      return new EntryIterator();
    }

    public boolean contains(Object o)
    {
      if (!(o instanceof LongIntegerHashMap.Entry))
        return false;
      LongIntegerHashMap.Entry<V> e = (LongIntegerHashMap.Entry<V>)o;
      long key = e.getKey();
      int hash = hash(key);
      int i = indexFor(hash, table.length);
      Entry<V> e1 = table[i];
      while (e1 != null && !(e1.hash == hash && key == e1.key))
        e1 = e1.next;
      Entry<V> candidate = e1;
      return candidate != null && candidate.equals(e);
    }

    public boolean remove(Object o)
    {
      return removeMapping(o) != null;
    }

    public int size()
    {
      return size;
    }

    public void clear()
    {
      LongIntegerHashMap.this.clear();
    }
  }
}
