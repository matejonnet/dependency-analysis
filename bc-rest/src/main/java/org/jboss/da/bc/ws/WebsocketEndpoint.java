package org.jboss.da.bc.ws;

import org.slf4j.Logger;

import javax.inject.Inject;
import javax.websocket.OnMessage;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 *
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
@ServerEndpoint("/ws")
public class WebsocketEndpoint {

    @Inject
    private Logger log;

    @Inject
    private Methods methods;

    @OnMessage
    public void onMessage(Session session, String msg) {
        Basic basic = session.getBasicRemote();
        try {
            JSONRPC2Request request = getRequest(basic, msg);
            if (request == null)
                return;
            String methodName = request.getMethod();

            if (!methods.contains(methodName)) {
                log.warn("Failed to find JSON RPC method " + methodName + ".");
                basic.sendText(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, request.getID())
                        .toString());
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode params = mapper.readTree(msg).get("params");
            if (params == null) {
                log.warn("Failed to parse JSON RPC parameters. Parameters are null.");
                JSONRPC2Error error = JSONRPC2Error.INVALID_PARAMS;
                error = error.setData("Parameter can't be null");
                basic.sendText(new JSONRPC2Response(error, request.getID()).toString());
                return;
            }

            Method method = methods.get(methodName);
            Object hello;
            try {
                hello = mapper.treeToValue(params, method.getParameterClass());
            } catch (JsonProcessingException ex) {
                log.warn("Failed to parse JSON RPC parameters", ex);
                JSONRPC2Error error = JSONRPC2Error.INVALID_PARAMS;
                error = error.setData(ex.getMessage());
                basic.sendText(new JSONRPC2Response(error, request.getID()).toString());
                return;
            }

            Object resp;
            try {
                resp = method.execute(hello);
            } catch (Exception ex) {
                log.error("Exception while executing method " + method, ex);
                JSONRPC2Error error = JSONRPC2Error.INTERNAL_ERROR;
                error = error.setData(ex.getMessage());
                basic.sendText(new JSONRPC2Response(error, request.getID()).toString());
                return;
            }

            Map<String, Object> objResp = mapper.convertValue(resp, Map.class);
            JSONRPC2Response response = new JSONRPC2Response(objResp, request.getID());
            if (!session.isOpen()) {
                log.warn("Session closed before response sent.");
                return;
            }
            basic.sendText(response.toJSONString());
        } catch (IOException ex) {
            log.error("Failed to process websocket request", ex);
        }
    }

    private JSONRPC2Request getRequest(Basic remote, String msg) throws IOException {
        try {
            return JSONRPC2Request.parse(msg);
        } catch (JSONRPC2ParseException ex) {
            JSONRPC2Error error;
            switch (ex.getCauseType()) {
                case JSONRPC2ParseException.PROTOCOL:
                    error = JSONRPC2Error.INVALID_REQUEST;
                    break;
                case JSONRPC2ParseException.JSON:
                    error = JSONRPC2Error.PARSE_ERROR;
                    break;
                default:
                    log.warn("Unknown exception cause type " + ex.getCauseType() + ".");
                    error = JSONRPC2Error.PARSE_ERROR;
                    break;
            }
            error = error.setData(ex.getMessage());
            remote.sendText(new JSONRPC2Response(error, null).toString());
            log.warn("Failed to parse JSON RPC message", ex);
            return null;
        }
    }
}
