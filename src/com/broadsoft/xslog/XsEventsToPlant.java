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

    private final static String XS_DATE = "";

    private final static Pattern HEAD_LINE = Pattern.compile("");

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

    public static void main(String[] args) throws IOException {
        Direction direction;
        String destination = null;
        String origin = null;
        List<String> messages = new ArrayList<>();

        Path path = FileSystems.getDefault().getPath(args[0]);
        List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
        Matcher matcher;
        int lineNumber = 0;
        for( String line : lines ) {
            ++lineNumber;
            //  direction line
            matcher = DIRECTION_LINE.matcher(line);
            if ( matcher.matches() ) {
                direction = matcher.group(1).startsWith("IN") ? Direction.IN : Direction.OUT;
                String target = "\"" + matcher.group(2) + "\"";
                switch(direction) {
                    case IN:
                        destination = "XS";
                        origin = target;
                        break;
                    case OUT:
                        destination = target;
                        origin = "XS";
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
