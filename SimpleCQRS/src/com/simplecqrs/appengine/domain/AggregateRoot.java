package com.simplecqrs.appengine.domain;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import com.simplecqrs.appengine.messaging.Event;

/**
 * Simple interface to an aggregate root
 */
public interface AggregateRoot {
	
	/**
	 * get the Id
	 * 
	 * @return
	 */
	UUID getId();
	
	/**
	 * get the aggregates events
	 * 
	 * @return
	 */
	Iterable<Event> getUncommittedChanges();
	
	/**
	 * Mark all changes a committed
	 */
	void markChangesAsCommitted();
	
	/**
	 * load the aggregate root
	 * 
	 * @param history
	 * @throws NoSuchMethodException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	void loadFromHistory(Iterable<Event> history) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException;
	
	/**
	 * Returns the expected version of the first new event
	 * @return
	 */
	int getExpectedVersion();
}