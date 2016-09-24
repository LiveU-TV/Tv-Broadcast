/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: GroupCallApp.java
 * Purpose	: SpringBootApplication runner    
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package liveu.tvbroadcast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import redsoft.dsagent.*;


@SpringBootApplication
@EnableWebSocket
public class GroupCallApp implements WebSocketConfigurer {

  @Bean
  public UserRegistry registry() {
    return new UserRegistry();
  }

  @Bean
//  @DependsOn("kurento")
  public RoomManager roomManager() {
	  RoomManager r = null;
	  try {
		  r = new RoomManager();
	  } catch (Exception e) {
		  e.printStackTrace(System.err);
		  //throw e;
	  }
	  return r;
  }

  @Bean
  public CallHandler groupCallHandler() {
    return new CallHandler();
  }

//  @Bean(name="kurento")
//  public KurentoClient kurentoClient() {
//    return KurentoClient.create();
//  }

  public static void main(String[] args) throws Exception {
	Settings.init();

	if (Settings.isPdsActive(PDs.PDS_STD))
		PDs.newStd();
	if (Settings.isPdsActive(PDs.PDS_ERR))
		PDs.newErr();
	if (Settings.isPdsActive(PDs.PDS_FILE))
		PDs.newFile(Settings.DS_FILEPATH);
	if (Settings.isPdsActive(PDs.PDS_UDP))
		PDs.newUdp(Settings.DS_UDPSRV);

	Ds.Init();
	Ds.dsSys.mainInfo("TVB-MAIN", "TV-Broadcast", "0.1");

	//Settings.print(System.err);
	Settings.print2Ds();

	SpringApplication.run(GroupCallApp.class, args);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(groupCallHandler(), "/groupcall");
  }
}
