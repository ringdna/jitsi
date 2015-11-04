package com.ringdna.chrome;

import net.java.sip.communicator.impl.gui.main.call.CallManager;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

public class NativeMessagingHost {

    private static final JSONParser json = new JSONParser();

    public static CommandProcessor commandProcessor;

    public NativeMessagingHost() {
        try {
            final InputStreamReader cin = new InputStreamReader(System.in);
            JSONObject msg = readMessage(cin);
            if (msg != null) {
                JSONObject response = new JSONObject();
                response.put("text", "Hello back9, " + msg.get("text"));
                sendMessage(response);
            }
            commandProcessor = new CommandProcessor(cin);
            new Thread(commandProcessor).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class CommandProcessor implements Runnable {
        private InputStreamReader cin;
        private net.java.sip.communicator.service.protocol.Call currentCall;
        public CommandProcessor(InputStreamReader cin) { this.cin = cin; }
        public void setCurrentCall(net.java.sip.communicator.service.protocol.Call value) {
            this.currentCall = value;
            JSONObject response = new JSONObject();
            response.put("text", "Set current call");
            sendMessage(response);
        }
        public void run() {
            JSONObject response = new JSONObject();
            response.put("text", "Started listener");
            sendMessage(response);

            while (true) {
                try {
                    Thread.currentThread().sleep(1000);
                    response = new JSONObject();
                    response.put("text", "ping");
                    sendMessage(response);

                    JSONObject newMessage = readMessage(cin);
                    if (newMessage != null) {
                        String command = (String)newMessage.get("text");
                        if (command.equals("accept")) {
                            new CallManager.AnswerCallThread(currentCall, null, false).start();
                            response = new JSONObject();
                            response.put("text", "Answered call");
                            sendMessage(response);
                        } else if (command.equals("disconnect")) {
                            new CallManager.HangupCallThread(currentCall).start();
                            response = new JSONObject();
                            response.put("text", "Disconnected call");
                            sendMessage(response);
                        } else {
                            response = new JSONObject();
                            response.put("text", "Received unknown command: " + command);
                            sendMessage(response);
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    response = new JSONObject();
                    response.put("text", "Exception: " + t.getMessage());
                    sendMessage(response);
                }
            }
        }
    }

    public static void newCall(CallEvent callEvent) {
        try {
            Iterator<? extends CallPeer> peersIter = callEvent.getSourceCall().getCallPeers();
            String peerAddress = peersIter.next().getAddress();
            JSONObject response = new JSONObject();
            response.put("type", "NewCall");
            response.put("from", peerAddress);
            sendMessage(response);
            if (commandProcessor == null) {
                JSONObject err = new JSONObject();
                err.put("error", "command processor is null");
                sendMessage(err);
            } else {
                commandProcessor.setCurrentCall(callEvent.getSourceCall());
            }
        } catch (Throwable t) {
            JSONObject err = new JSONObject();
            err.put("error", t.getMessage());
            sendMessage(err);
        }
    }

    private static JSONObject readMessage(InputStreamReader cin) {
        try {
            char[] len = new char[4];
            cin.read(len);
            int size = readMessageSize(len);
            char[] string = new char[size];
            cin.read(string);
            return (JSONObject)json.parse(new String(string));
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void sendMessage(JSONObject response) {
        try {
            String message = response.toString();
            System.out.write(writeMessageSize(message.length()));
            System.out.write(message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int readMessageSize(char[] bytes) {
        return  (bytes[3]<<24) & 0xff000000|
                (bytes[2]<<16) & 0x00ff0000|
                (bytes[1]<< 8) & 0x0000ff00|
                (bytes[0]<< 0) & 0x000000ff;
    }

    private static byte[] writeMessageSize(int length) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ( length      & 0xFF);
        bytes[1] = (byte) ((length>>8)  & 0xFF);
        bytes[2] = (byte) ((length>>16) & 0xFF);
        bytes[3] = (byte) ((length>>24) & 0xFF);
        return bytes;
    }
}
