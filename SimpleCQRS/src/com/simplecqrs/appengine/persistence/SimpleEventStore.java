package com.simplecqrs.appengine.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.simplecqrs.appengine.messaging.Event;
import com.simplecqrs.appengine.messaging.MessageLog;

/**
 * Basic implementation of an event store that persists
 * and retrieves from a Google App Engine Datastore 
 */
public class SimpleEventStore implements EventStore {
	
	/**
	 * The name of the kind/schema
	 */
	private static final String KIND = "EventStore";
	
	/**
	 * Property name for the list of events in storage
	 */
	private static final String EVENTS_PROPERTY = "Events";
	
	@SuppressWarnings("unchecked")
	@Override
	public void saveEvents(UUID aggregateId, int expectedVersion, Iterable<Event> events) throws EventCollisionException {
		
		if(events != null){
			Transaction transaction = null;
			
			try{
			
				DatastoreService dataStore = DatastoreServiceFactory.getDatastoreService();
				transaction = dataStore.beginTransaction();
				
				Key key = KeyFactory.createKey(KIND, aggregateId.toString());		
				Entity entity = null;
				
				try {
					entity = dataStore.get(transaction,key);
				} catch (EntityNotFoundException e) {
					// Not a problem, just continue on. It is a new aggregate
				}

				List<String> entityEvents = null;
				
				if(entity == null){
					entity = new Entity(KIND, aggregateId.toString());
					entityEvents = new ArrayList<String>();
				}
				else{
				
					entityEvents = (List<String>)entity.getProperty(EVENTS_PROPERTY);
					
					long currentVersion = entityEvents.size();
					
					if(currentVersion >= expectedVersion)
					{
						transaction.rollback();
						
						throw new EventCollisionException(aggregateId, expectedVersion);
					}
				}
				
				Gson gson = new Gson();
				
				//convert all of the new events to json for storage
				for(Event event : events){
					
					String eventJson = gson.toJson(event);
					String kind = event.getClass().getName();
					
					EventModel newEvent = new EventModel(kind, eventJson, new Long(expectedVersion));
					
					String json = gson.toJson(newEvent);
					entityEvents.add(json);
					
					//increment the expected version
					expectedVersion++;
				}
				
				entity.setUnindexedProperty(EVENTS_PROPERTY, entityEvents);
				
				
				dataStore.put(entity);
				
				transaction.commit();
				
				//for(Event event : events){
					//Publish the event for listeners
					//MessageBus.getInstance().publish(event);
				//}

			} catch (Exception e){
				if(transaction != null &&  transaction.isActive())
					transaction.rollback();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private List<Event> hydrateEvents(Entity entity){
		
		Gson gson = new Gson();
		
		List<String> events = (List<String>)entity.getProperty(EVENTS_PROPERTY);
		List<Event> history = new ArrayList<Event>();
		
		for(String row : events){
			
			EventModel model = gson.fromJson(row, EventModel.class);
			
			try {
				
				Event event = (Event) gson.fromJson(model.getJson(), Class.forName(model.getKind()));
				history.add(event);
				
			} catch (JsonSyntaxException e) {
				MessageLog.log(e);
			} catch (ClassNotFoundException e) {
				MessageLog.log(e);
			}
		}
		
		return history;
	}

	@Override
	public Iterable<Event> getEvents(UUID aggregateId) {

		DatastoreService dataStore = DatastoreServiceFactory.getDatastoreService();
		
		Key key = KeyFactory.createKey(KIND, aggregateId.toString());		
		Entity entity = null;
		
		try {
			entity = dataStore.get(key);
		} catch (EntityNotFoundException e) {
			/*
			 * Return null because this entity doesn't exist in the store
			 */
			return null;
		}

		return hydrateEvents(entity);
	}
}
