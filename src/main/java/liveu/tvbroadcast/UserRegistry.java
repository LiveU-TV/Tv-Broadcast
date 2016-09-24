/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: UserRegistry.java
 * Purpose	: User Registry (collection of UserSession)    
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package liveu.tvbroadcast;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.WebSocketSession;

import redsoft.dsagent.*;


/**
 * Map of users registered in the system. This class has a concurrent hash map to store users, using
 * its name as key in the map.
 */
public class UserRegistry {

  private final Ds ds = new Ds(getClass().getSimpleName());
  
  private final ConcurrentHashMap<String, UserSession> sessionZ = new ConcurrentHashMap<>(); 
 
  public boolean register(UserSession user) {
		ds.print("UserRegistry::register user=%s", user.getName());
	  
		if (this.sessionZ.containsKey(user.getSession().getId())) {
			return false;
		}

    sessionZ.put(user.getSession().getId(), user);
    return true;
  }

  public UserSession getByName(String name) {
	  for (UserSession u : sessionZ.values()) {
		  String n = u.getName();
		  if (null != n && n.equals(name)) return u;
	  }
	  return null;
  }

  public UserSession getBySession(WebSocketSession session) {
    return sessionZ.get(session.getId());    
  }
  
  public Collection<UserSession> getUserSessions() {
	  return sessionZ.values();
  }

  public boolean exists(String name) {
	  if (null != getByName(name)) return true;
	  return false;
  }

  public UserSession removeBySession(WebSocketSession session) {
    final UserSession user = getBySession(session);
    sessionZ.remove(session.getId());
    return user;
  }

}
