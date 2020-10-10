package dev.jarcadia.service;

import dev.jarcadia.vimes.model.LocalProcessResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LocalProcessService {

    public static LocalProcessResult run(long timeout, TimeUnit unit, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = pb.start();
        List<String> output = new LinkedList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            while (true)
            {
                String line = in.readLine();
                if (line == null)
                    break;
                output.add(line);
            }
        }
        process.waitFor();
        return new LocalProcessResult(process.exitValue(), output);
    }

}
