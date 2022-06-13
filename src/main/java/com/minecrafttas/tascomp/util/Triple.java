package com.minecrafttas.tascomp.util;


/**
 * Really simple triple class to avoid using a library
 * @author Scribble
 *
 * @param <L> The leftmost class
 * @param <M> The middle class
 * @param <R> The rightmost class
 */
public class Triple<L, M, R> {
	
	private L left;
	
	private M middle;
	
	private R right;
	
	public Triple(L left, M middle, R right) {
		this.left=left;
		this.middle=middle;
		this.right=right;
	}

	public static <L, M, R>Triple<L, M, R> of(L left, M middle, R right) {
		return new Triple<L, M, R>(left, middle, right);
	}
	
	public L getLeft() {
		return left;
	}
	
	public M getMiddle() {
		return middle;
	}
	
	public R getRight() {
		return right;
	}
}
