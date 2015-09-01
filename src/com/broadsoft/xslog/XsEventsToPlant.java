package com.broadsoft.xslog;


import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XsEventsToPlant {

    enum Direction {
        IN,
        OUT
    }

    private final static String XS_DATE = "\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}:\\d{1,3}";
    private final static String XS_DEVICE = "[0-9.@+]+";
    private final static String XS_SESSION_ID = "(callhalf-[0-9:]+)";
    private final static String TYPE = "(?:Sip|SipMedia)"; // TODO: capture this to distinguish between SipEndpoint and SipMediaEndpoint ?


    private final static Pattern HEAD_LINE = Pattern.compile(XS_DATE
            + " EDT \\| Info\\s{7}\\| "
            + TYPE + " \\| "
            + XS_DEVICE + " \\| "
            + XS_SESSION_ID );

    private final static String XS_EVENT_ACTION = "\\t(?:Resuming|Processing|Transforming) Event: ";
    private final static String XS_EVENT = "com\\.broadsoft\\.(?:\\w|\\.|)+?\\.(\\w+Event)";
    private final static String XS_EVENT_END = "\\s?(.*)$";
    private final static String XS_SESSION = "((?:\\w|-|:|\\.)+)";
    private final static Pattern XS_SESSION_PAIR = Pattern.compile("\\{" + XS_SESSION + "," + XS_SESSION + "}");
    private final static Pattern EVENT_LINE = Pattern.compile(XS_EVENT_ACTION + XS_EVENT + XS_EVENT_END);

    private final static Pattern DIRECTION_LINE =
            Pattern.compile("\\tudp \\d+ Bytes (IN from|OUT to) (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):\\d{1,5}");
    private final static Pattern SIP_RESPONSE_LINE =
            Pattern.compile("SIP/2.0 (\\d{3}).*?");
    private static final String METHOD =
            "(INVITE|ACK|BYE|CANCEL|OPTIONS|REGISTER|PRACK|SUBSCRIBE|NOTIFY|PUBLISH|INFO|REFER|MESSAGE|UPDATE)";
    private final static Pattern SIP_REQUEST_LINE =
            Pattern.compile(METHOD + " sip:.*?");



    public static String createMessage(String message, String origin, String destination, int lineNumber) {
        return origin + " -> " + destination + " : " + "[[" + lineNumber + "]] " + message + "\n";
    }

    public static String replaceService(String target) {
        if ( target.startsWith("\"CHCallService") ) {
            target = target.replace("CHCallService", "CHCall");
        }
        return target;
    }

    public static void main(String[] args) throws IOException {
        Direction direction;
        String destination = null;
        String origin = null;
        String sipEndPoint = null;
        List<String> messages = new ArrayList<>();

        Path path = FileSystems.getDefault().getPath(args[0]);
        List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
        Matcher matcher;
        int lineNumber = 0;
        for( String line : lines ) {
            ++lineNumber;

            // event line
            matcher = EVENT_LINE.matcher(line);
            if ( matcher.matches() ) {
                String event = matcher.group(1);
                String sessionPair = matcher.group(2);
                if ( sessionPair != null && !sessionPair.isEmpty() ) {
                    matcher = XS_SESSION_PAIR.matcher( sessionPair );
                    if ( matcher.find() ) {
                        origin = "\"" + matcher.group(1) + "\"";
                        origin = replaceService(origin);
                        destination = "\"" + matcher.group(2) + "\"";
                        destination = replaceService(destination);
                        messages.add(createMessage(event, origin, destination, lineNumber) );
                    }
                }
                continue;
            }

            // headline
            matcher = HEAD_LINE.matcher(line);
            if ( matcher.matches() ) {
                sipEndPoint = "\"" + "SipEndpoint." + matcher.group(1) + "\"";
                continue;
            }


            //  direction line
            matcher = DIRECTION_LINE.matcher(line);
            if ( matcher.matches() ) {
                direction = matcher.group(1).startsWith("IN") ? Direction.IN : Direction.OUT;
                String target = "\"" + matcher.group(2) + "\"";
                switch(direction) {
                    case IN:
                        destination = sipEndPoint;
                        origin = target;
                        break;
                    case OUT:
                        destination = target;
                        origin = sipEndPoint;
                        break;
                }
                continue;
            }
            // sip request line
            matcher = SIP_REQUEST_LINE.matcher(line);
            if ( matcher.matches() ) {
                messages.add(createMessage(matcher.group(1), origin, destination, lineNumber));
                continue;
            }

            // sip response line
            matcher = SIP_RESPONSE_LINE.matcher(line);
            if ( matcher.matches() ) {
                messages.add(createMessage(matcher.group(1), origin, destination, lineNumber));
                continue;
            }

        }

        StringBuilder plant =  new StringBuilder();
        plant.append("@startuml\n");
        for (String message : messages ) {
            plant.append(message);
        }
        plant.append("@enduml\n");
        System.out.println( plant.toString() );
    }
}
