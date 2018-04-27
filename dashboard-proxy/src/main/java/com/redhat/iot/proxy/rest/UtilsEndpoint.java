/*
 * ******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc. and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *
 * ******************************************************************************
 */
package com.redhat.iot.proxy.rest;

import com.redhat.iot.proxy.model.*;
import com.redhat.iot.proxy.service.AlertsService;
import com.redhat.iot.proxy.service.DGService;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A simple REST service which proxies requests to a local datagrid.
 */

@Path("/utils")
@Singleton
public class UtilsEndpoint {

    public static final long MS_IN_HOUR = 60 * 60 * 1000;

    @Inject
    DGService dgService;

    @Inject
    AlertsService alertsService;

    @GET
    @Path("/health")
    public Response health() {
//        if (!alertsService.isConnected()) {
//            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Broker disconnected").build();
//        }
        return Response.ok().build();
    }

    @POST
    @Path("/simulator/alert/{fid}/{lid}/{mid}")
    @Consumes({"application/json"})
    public void simulatorAlert(ControlMessage msg,
                               @PathParam("fid") String fid,
                               @PathParam("lid") String lid,
                               @PathParam("mid") String mid) throws Exception {

        String topic = "x/y/z/facilities/" + fid + "/lines/" + lid + "/machines/" + mid + "/alerts";

        JSONObject payload = new JSONObject();

        payload.put("timestamp", msg.getTimestamp());
        payload.put("id", msg.getId());
        payload.put("type", msg.getType());
        payload.put("description", msg.getDescription());
        payload.put("details", new JSONObject(msg.getDetails()));

        MqttMessage mqttMessage = new MqttMessage(payload.toString().getBytes());

        alertsService.messageArrived(topic, mqttMessage);
    }

    @POST
    @Path("/resetAll")
    public void resetAll() {

        Map<String, Customer> customerCache = dgService.getCustomers();
        Map<String, Facility> facilitiesCache = dgService.getFacilities();
        Map<String, Line> linesCache = dgService.getProductionLines();
        Map<String, Machine> machinesCache = dgService.getMachines();
        Map<String, Run> runsCache = dgService.getRuns();
        Map<String, CalEntry> calendarCache = dgService.getCalendar();


        facilitiesCache.clear();
        linesCache.clear();
        customerCache.clear();
        machinesCache.clear();
        runsCache.clear();
        calendarCache.clear();

        for (String COMPANY : COMPANIES) {
            customerCache.put(COMPANY,
                    new Customer(COMPANY, "password"));
        }

        for (String[] facility : FACILITIES) {
            populateFacility(facility);
        }
    }

    @POST
    @Path("/reset/{fid}")
    public void resetFacility(@PathParam("fid") String fid) {

        Map<String, Facility> facilitiesCache = dgService.getFacilities();
        Map<String, Line> linesCache = dgService.getProductionLines();
        Map<String, Machine> machinesCache = dgService.getMachines();
        Map<String, Run> runsCache = dgService.getRuns();
        Map<String, CalEntry> calendarCache = dgService.getCalendar();

        Facility f = facilitiesCache.get(fid);
        if (f == null) {
            return;
        }

        facilitiesCache.remove(fid);
        for (Line l : f.getLines()) {
            linesCache.remove(f.getFid() + "/" + l.getLid());
            for (Machine m : l.getMachines()) {
                machinesCache.remove(f.getFid() + "/" + l.getLid() + " / " + m.getMid());
            }
            runsCache.remove(l.getCurrentRun().getRid());

            List<CalEntry> oldEntries = calendarCache.keySet()
                    .stream().map(calendarCache::get)
                    .filter(c -> c.getFacility().getFid().equals(fid))
                    .collect(Collectors.toList());

            oldEntries.forEach(calEntry -> calendarCache.remove(calEntry.getCid()));

        }

        for (String[] facility : FACILITIES) {
            if (facility[1].equals(fid)) {
                populateFacility(facility);
            }
        }

    }

    private void populateFacility(String[] facility) {

        Map<String, Customer> customerCache = dgService.getCustomers();
        Map<String, Facility> facilitiesCache = dgService.getFacilities();
        Map<String, Line> linesCache = dgService.getProductionLines();
        Map<String, Machine> machinesCache = dgService.getMachines();
        Map<String, Run> runsCache = dgService.getRuns();
        Map<String, CalEntry> calendarCache = dgService.getCalendar();

        Facility newFacility = new Facility();
        newFacility.setName(facility[0]);
        newFacility.setFid(facility[1]);
        newFacility.setCapacity(Math.round(Math.random() * 10000));
        newFacility.setLocation(new LatLng(20, -80));
        newFacility.setAddress(newFacility.getName());
        newFacility.setUtilization(.90 + (.1 * Math.random()));

        List<Line> lines = new ArrayList<>();

        for (String[] line : LINES) {
            Line newLine = new Line();
            newLine.setCurrentFid(newFacility.getFid());
            lines.add(newLine);
            newLine.setName(line[0]);
            newLine.setLid(line[1]);
            newLine.setDesc("The line");
            newLine.setStatus("ok");

            List<Machine> machines = new ArrayList<>();

            for (String[] machine : MACHINES) {
                Machine newMachine = new Machine();
                machines.add(newMachine);
                newMachine.setName(machine[0]);
                newMachine.setMid(machine[1]);
                newMachine.setStatus("ok");
                newMachine.setDesc("The machine");
                List<Telemetry> machineTelemetry = new ArrayList<>();
                machineTelemetry.add(new Telemetry("A", 55, 15, "Current", "current"));
                machineTelemetry.add(new Telemetry("°C", 90, 10, "Temperature", "temp"));
                machineTelemetry.add(new Telemetry("db", 40, 30, "Noise", "noise"));
                machineTelemetry.add(new Telemetry("rpm", 2000, 1500, "Speed", "speed"));
                machineTelemetry.add(new Telemetry("nu", .5, 0.05, "Vibration", "vibration"));
                machineTelemetry.add(new Telemetry("V", 250, 200, "Voltage", "voltage"));
                newMachine.setTelemetry(machineTelemetry);
                newMachine.setCurrentFid(newFacility.getFid());
                newMachine.setCurrentLid(newLine.getLid());
                newMachine.setType(machine[2]);
                machinesCache.put(newFacility.getFid() + "/" + newLine.getLid() + "/" + newMachine.getMid(), newMachine);


            }

            newLine.setMachines(machines);

            Date now = new Date();

            Run r = new Run();
            r.setName(rand(RUNS));
            r.setRid(UUID.randomUUID().toString());
            r.setCustomer(customerCache.get(rand(COMPANIES)));
            r.setDesc("Standard Run");
            newLine.setCurrentRun(r);
            r.setStatus("ok");
            r.setStart(new Date(now.getTime() - ((int) (Math.floor((Math.random() * 2.0) * (double) MS_IN_HOUR)))));
            r.setEnd(new Date(now.getTime() + ((int) (Math.floor((Math.random() * 4.0) * (double) MS_IN_HOUR)))));

            CalEntry runCalEntry = new CalEntry();
            runCalEntry.setCid(UUID.randomUUID().toString());
            runCalEntry.setTitle(r.getName() + "(" + r.getCustomer().getName() + ")");
            runCalEntry.setStart(r.getStart());
            runCalEntry.setEnd(r.getEnd());
            runCalEntry.setFacility(newFacility);
            runCalEntry.setColor(line[2]);
            runCalEntry.setType("run");
            runCalEntry.setDetails(new JSONObject().put("desc", "The Run").toString());
            calendarCache.put(runCalEntry.getCid(), runCalEntry);

            runsCache.put(r.getRid(), r);

            linesCache.put(newFacility.getFid() + "/" + newLine.getLid(), newLine);


        }

        newFacility.setLines(lines);

        facilitiesCache.put(newFacility.getFid(), newFacility);
    }

    private String rand(String[] strs) {
        return strs[(int) Math.floor(Math.random() * strs.length)];
    }

    @GET
    @Path("/summaries")
    @Produces({"application/json"})
    public List<Summary> getSummaries() {

        List<Summary> result = new ArrayList<>();

        Summary customerSummary = getClientSummary();
        Summary runsSummary = getRunsSummary();
        Summary linesSummary = getLinesSummary();
        Summary facilitySummary = getFacilitySummary();
        Summary machinesSummary = getMachinesSummary();

        result.add(customerSummary);
        result.add(runsSummary);
        result.add(linesSummary);
        result.add(facilitySummary);
        result.add(machinesSummary);

        Summary operatorsSummary = new Summary();
        operatorsSummary.setName("operators");
        operatorsSummary.setTitle("Operators");
        operatorsSummary.setCount(23);
        operatorsSummary.setWarningCount(4);
        operatorsSummary.setErrorCount(1);
        result.add(operatorsSummary);
        return result;
    }

    private Summary getFacilitySummary() {
        Map<String, Facility> cache = dgService.getFacilities();

        Summary summary = new Summary();
        summary.setName("facilities");
        summary.setTitle("Facilities");
        summary.setCount(cache.keySet().size());

        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> v.getUtilization() < .7 && v.getUtilization() > .5)
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> v.getUtilization() < .5)
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);

        return summary;
    }

    private Summary getClientSummary() {
        Map<String, Customer> cache = dgService.getCustomers();

        Summary summary = new Summary();
        summary.setName("clients");
        summary.setTitle("Clients");
        summary.setCount(cache.keySet().size());
        return summary;

    }

    private Summary getLinesSummary() {
        Map<String, Run> cache = dgService.getRuns();

        Summary summary = new Summary();
        summary.setName("lines");
        summary.setTitle("Lines");
        summary.setCount(cache.keySet().size());

        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("warning"))
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("error"))
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);

        return summary;

    }

    private Summary getRunsSummary() {
        Map<String, Run> cache = dgService.getRuns();

        Summary summary = new Summary();
        summary.setName("runs");
        summary.setTitle("Runs");
        summary.setCount(cache.keySet().size());
        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("warning"))
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("error"))
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);

        return summary;

    }

    private Summary getMachinesSummary() {
        Map<String, Machine> cache = dgService.getMachines();

        Summary summary = new Summary();
        summary.setName("runs");
        summary.setTitle("Runs");
        summary.setCount(cache.keySet().size());
        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("warning"))
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(r -> r.getStatus().equalsIgnoreCase("error"))
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);

        return summary;

    }

    public static final String[] COMPANIES = new String[]{
            "Wonka Industries",
            "Acme Corp",
            "Stark Industries",
            "Ollivander's Wand Shop",
            "Gekko & Co",
            "Wayne Enterprises",
            "Cyberdyne Systems",
            "Cheers",
            "Genco Pura",
            "NY Enquirer",
            "Duff Beer",
            "Bubba Gump Shrimp Co",
            "Olivia Pope & Associates",
            "Sterling Cooper",
            "Soylent",
            "Hooli",
            "Good Burger"
    };

    public static final String[][] FACILITIES = new String[][]{
            {"Atlanta", "facility-1"},
            {"Singapore", "facility-2"},
            {"Frankfurt", "facility-3"},
            {"Raleigh", "facility-4"}
    };

    public static final String[][] LINES = new String[][]{
            {"Line 1", "line-1", "#9ecf99"},
            {"Line 2", "line-2", "#9ecf99"},
            {"Line 3", "line-3", "#9ecf99"},
            {"Line 4", "line-4", "#9ecf99"},
            {"Line 5", "line-5", "#9ecf99"},
            {"Line 6", "line-6", "#9ecf99"},
            {"Line 7", "line-7", "#9ecf99"},
            {"Line 8", "line-8", "#9ecf99"}
    };

    public static final String[][] MACHINES = new String[][]{
            {"Caster", "machine-1", "caster"},
            {"Chiller", "machine-2", "chiller"},
            {"Weighting", "machine-3", "scale"},
            {"Spin Test", "machine-4", "spinner"},
            {"Caster", "machine-5", "caster"},
            {"Chiller", "machine-6", "chiller"},
            {"Weighting", "machine-7", "scale"},
            {"Spin Test", "machine-8", "spinner"}
    };

    public static final String[] RUNS = new String[]{
            "500-FS",
            "240-DS",
            "10000-TP"
    };


}

