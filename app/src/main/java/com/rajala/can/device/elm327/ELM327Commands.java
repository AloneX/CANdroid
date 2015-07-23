package com.rajala.can.device.elm327;

public interface ELM327Commands {

    String AT_COMMAND = "AT";

    String AT_ALLOW_LONG                = AT_COMMAND + "AL";
    String AT_DISPLAY_DLC               = AT_COMMAND + "D%d";
    String AT_DESCRIBE_PROTOCOL_NUMBER  = AT_COMMAND + "DPN";
    String AT_LINEFEEDS                 = AT_COMMAND + "L%d";
    String AT_MONITOR_ALL               = AT_COMMAND + "MA";
    String AT_RESPONSES                 = AT_COMMAND + "R%d";
    String AT_SPACES                    = AT_COMMAND + " S%d";
    String AT_SET_HEADER                = AT_COMMAND + "SH%s";
    String AT_SET_HEADER_XYZ            = AT_COMMAND + "SH%02X%02X%02X";
    String AT_SET_HEADER_YZ             = AT_COMMAND + "SH%01X%02X";
    String AT_SET_PROTOCOL_AUTO         = AT_COMMAND + "SP00";
    String AT_SET_PROTOCOL              = AT_COMMAND + "SP%d";
    String AT_RESET_ALL                 = AT_COMMAND + "Z";
    String AT_BAUD_RATE_DIVISOR         = AT_COMMAND + "BRD%d";
    String AT_REPEAT                    = AT_COMMAND + "\r";
    String AT_ECHO                      = AT_COMMAND + "E%d";
    String AT_DEFAULTS                  = AT_COMMAND + "D";
    String AT_HEADERS                   = AT_COMMAND + "H%d";
}