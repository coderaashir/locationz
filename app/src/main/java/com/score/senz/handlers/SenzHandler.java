package com.score.senz.handlers;

import com.score.senz.pojos.Senz;
import com.score.senz.utils.SenzParser;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

/**
 * Handle All senz messages from here
 */
public class SenzHandler {
    private static SenzHandler instance;

    private SenzHandler() {
    }

    public static SenzHandler getInstance() {
        if (instance == null) {
            instance = new SenzHandler();
        }
        return instance;
    }

    public void handleSenz(String senzMessage) {
        try {
            // parse senz
            Senz senz = SenzParser.parse(senzMessage);
            switch (senz.getSenzType()) {
                case SHARE:
                    handleShareSenz(senz);
                case GET:
                    handleGetSenz(senz);
                case DATA:
                    handleDataSenz(senz);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }

    }

    private boolean verifySenz(Senz senz) {

    }

    private void handleShareSenz(Senz senz) {
        //
    }

    private void handleGetSenz(Senz senz) {
        //
    }

    private void handleDataSenz(Senz senz) {
        //
    }
}
