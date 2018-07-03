package org.aion.avm.shadow.java.lang;

import org.aion.avm.arraywrapper.CharArray;

public class StringBuffer extends Object implements CharSequence, Appendable{

    public StringBuffer() {
        this.v = new java.lang.StringBuffer();
    }

    public StringBuffer(int capacity) {
        this.v = new java.lang.StringBuffer(capacity);
    }

    public StringBuffer(String str) {
        this.v = new java.lang.StringBuffer(str.getUnderlying());
    }

    public StringBuffer(CharSequence seq) {
        this(seq.avm_length() + 16);
        avm_append(seq);
    }

    public synchronized int avm_length() {
        return this.v.length();
    }

    public synchronized int avm_capacity() {
        return this.v.capacity();
    }

    public synchronized void avm_ensureCapacity(int minimumCapacity){
        this.v.ensureCapacity(minimumCapacity);
    }

    public synchronized void avm_trimToSize() {
        this.v.trimToSize();
    }

    public synchronized void avm_setLength(int newLength) {
        this.v.setLength(newLength);
    }

    public synchronized char avm_charAt(int index) {
        return this.v.charAt(index);
    }

    public synchronized int avm_codePointAt(int index) {
        return this.v.codePointAt(index);
    }

    public synchronized int avm_codePointBefore(int index) {
        return this.v.codePointBefore(index);
    }

    public synchronized int avm_codePointCount(int beginIndex, int endIndex) {
        return this.v.codePointCount(beginIndex, endIndex);
    }

    public synchronized int avm_offsetByCodePoints(int index, int codePointOffset) {
        return this.v.offsetByCodePoints(index, codePointOffset);
    }

    public synchronized void avm_getChars(int srcBegin, int srcEnd, char[] dst,
                                      int dstBegin)
    {
        this.v.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    public synchronized void avm_setCharAt(int index, char ch) {
        this.v.setCharAt(index, ch);
    }

    //TODO: IOBJECT?
    public synchronized StringBuffer avm_append(Object obj) {
        this.v = this.v.append(obj);
        return this;
    }

    public synchronized StringBuffer avm_append(String str) {
        this.v = this.v.append(str);
        return this;
    }

    public synchronized StringBuffer avm_append(StringBuffer sb) {
        this.v = this.v.append(sb.v);
        return this;
    }

    public synchronized StringBuffer avm_append(CharSequence s){
        this.v = this.v.append(s.avm_toString());
        return this;
    }

    public synchronized StringBuffer avm_append(CharSequence s, int start, int end){
        this.v = this.v.append(s.avm_toString().getUnderlying(), start, end);
        return this;
    }

    public synchronized StringBuffer avm_append(CharArray str) {
        this.v = this.v.append(str.getUnderlying());
        return this;
    }

    public synchronized StringBuffer avm_append(CharArray str, int offset, int len) {
        this.v = this.v.append(str.getUnderlying(), offset, len);
        return this;
    }

    public synchronized StringBuffer avm_append(boolean b) {
        this.v = this.v.append(b);
        return this;
    }

    public synchronized StringBuffer avm_append(char c) {
        this.v = this.v.append(c);
        return this;
    }

    public synchronized StringBuffer avm_append(int i) {
        this.v = this.v.append(i);
        return this;
    }

    public synchronized StringBuffer avm_appendCodePoint(int codePoint) {
        this.v = this.v.appendCodePoint(codePoint);
        return this;
    }

    public synchronized StringBuffer avm_append(long lng) {
        this.v = this.v.append(lng);
        return this;
    }

    public synchronized StringBuffer avm_append(float f) {
        this.v = this.v.append(f);
        return this;
    }

    public synchronized StringBuffer avm_append(double d) {
        this.v = this.v.append(d);
        return this;
    }

    public synchronized StringBuffer avm_delete(int start, int end) {
        this.v = this.v.delete(start, end);
        return this;
    }

    public synchronized StringBuffer avm_deleteCharAt(int index) {
        this.v = this.v.deleteCharAt(index);
        return this;
    }

    public synchronized StringBuffer avm_replace(int start, int end, String str) {
        this.v = this.v.replace(start, end, str.getUnderlying());
        return this;
    }

    public synchronized String avm_substring(int start) {
        return new String(this.v.substring(start));
    }

    public synchronized CharSequence avm_subSequence(int start, int end){
        return new String(this.v.subSequence(start, end).toString());
    }

    public synchronized String avm_substring(int start, int end) {
        return new String(this.v.substring(start, end));
    }

    public synchronized StringBuffer avm_insert(int index, CharArray str, int offset,
                                            int len)
    {
        this.v.insert(index, str.getUnderlying(), offset, len);
        return this;
    }

    //TODO: IOBJECT?
    public synchronized StringBuffer avm_insert(int offset, Object obj) {
        this.v.insert(offset, obj);
        return this;
    }

    public synchronized StringBuffer avm_insert(int offset, String str) {
        this.v.insert(offset, str.getUnderlying());
        return this;
    }

    public synchronized StringBuffer avm_insert(int offset, CharArray str) {
        this.v.insert(offset, str.getUnderlying());
        return this;
    }

    public StringBuffer avm_insert(int dstOffset, CharSequence s){
        this.v.insert(dstOffset, s.avm_toString());
        return this;
    }

    public synchronized StringBuffer avm_insert(int dstOffset, CharSequence s, int start, int end) {
        this.v.insert(dstOffset, s.avm_subSequence(start, end));
        return this;
    }

    public StringBuffer avm_insert(int offset, boolean b) {
        this.v.insert(offset, b);
        return this;
    }

    public synchronized StringBuffer avm_insert(int offset, char c) {
        this.v.insert(offset, c);
        return this;
    }

    public StringBuffer avm_insert(int offset, int i) {
        this.v.insert(offset, i);
        return this;
    }

    public StringBuffer avm_insert(int offset, long l) {
        this.v.insert(offset, l);
        return this;
    }

    public StringBuffer avm_insert(int offset, float f) {
        this.v.insert(offset, f);
        return this;
    }

    public StringBuffer avm_insert(int offset, double d) {
        this.v.insert(offset, d);
        return this;
    }

    public int avm_indexOf(String str) {
        return this.v.indexOf(str.getUnderlying());
    }

    public synchronized int avm_indexOf(String str, int fromIndex) {
        return this.v.indexOf(str.getUnderlying(), fromIndex);
    }

    public int avm_lastIndexOf(String str) {
        return this.v.lastIndexOf(str.getUnderlying());
    }

    public synchronized int avm_lastIndexOf(String str, int fromIndex) {
        return this.v.lastIndexOf(str.getUnderlying(), fromIndex);
    }

    public synchronized StringBuffer avm_reverse() {
        this.v.reverse();
        return this;
    }

    public synchronized String avm_toString() {
        return new String(this);
    }

    //========================================================
    // Methods below are used by runtime and test code only!
    //========================================================
    private java.lang.StringBuffer v;

    public java.lang.StringBuffer getUnderlying() {
        return v;
    }


    //========================================================
    // Methods below are deprecated, we don't shadow them
    //========================================================



    //========================================================
    // Methods below are excluded from shadowing
    //========================================================
}