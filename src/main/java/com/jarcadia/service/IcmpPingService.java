package com.jarcadia.service;

import com.jarcadia.watchdog.model.IcmpPingResult;
import com.jarcadia.watchdog.model.LocalProcessResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

public class IcmpPingService {

    public static IcmpPingResult ping(String hostname) {
        try {
            LocalProcessResult result = LocalProcessService.run(5, TimeUnit.SECONDS, "ping",
                    "-c", "1", hostname);
            if (result.getExitCode() == 0) {
                String output = result.getOutput().get(1);
                int start = output.indexOf("time=") + 5;
                int end = output.indexOf(" ms", start);
                long millis = new BigDecimal(output.substring(start, end))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
                return new IcmpPingResult(IcmpPingResult.Status.SUCCESS, millis);
            } else {
                return new IcmpPingResult(IcmpPingResult.Status.FAILED, -1);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new IcmpPingResult(IcmpPingResult.Status.FAILED, -1);
        } catch (InterruptedException e) {
            return new IcmpPingResult(IcmpPingResult.Status.TIMEOUT, 5000);
        }
    }
}
