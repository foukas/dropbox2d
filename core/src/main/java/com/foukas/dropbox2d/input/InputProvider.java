package com.foukas.dropbox2d.input;

/** Produces a steering value in [-1, 1] (negative = left, positive =
 * right, 0 = no input) from whatever input source the implementation
 * reads. DropGame applies steering force identically regardless of which
 * provider is active -- built only now that Approach B1 (tilt) actually
 * exists, per the earlier eng-review call to not build this abstraction
 * speculatively ahead of a second real input scheme. */
public interface InputProvider {
    float getSteerValue();
}
