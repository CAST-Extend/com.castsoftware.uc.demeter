/*
 * Copyright (C) 2020  Hugo JOBY
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License v3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public v3
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.castsoftware.demeter.models;

import com.castsoftware.demeter.config.Configuration;
import com.castsoftware.demeter.database.Neo4jAL;
import com.castsoftware.demeter.exceptions.neo4j.*;
import com.castsoftware.demeter.tags.TagProcessing;
import org.neo4j.graphdb.*;

import java.util.*;

public class StatisticNode extends Neo4jObject {

    private static final String LABEL = Configuration.get("neo4j.nodes.t_statistic");
    private static final String NAME_PROPERTY = Configuration.get("neo4j.nodes.t_statistic.name");
    private static final String REQUEST_PROPERTY = Configuration.get("neo4j.nodes.t_statistic.request");
    private static final String ACTIVE_PROPERTY = Configuration.get("neo4j.nodes.t_statistic.active");
    private static final String DESCRIPTION_PROPERTY = Configuration.get("neo4j.nodes.t_statistic.description");
    private static final String ERROR_PREFIX = Configuration.get("neo4j.nodes.t_statistic.error_prefix");
    private static final String CONF_TO_STAT_REL = Configuration.get("neo4j.relationships.use_case.to_stats");

    private static final String STAT_RETURN_STRING = Configuration.get("tag.anchors.statistics.return_as_string_val");

    private String name;
    private String request;
    private Boolean active;
    private String description;

    public static String getLabel() {
        return LABEL;
    }
    public static String getNameProperty() {
        return NAME_PROPERTY;
    }
    public static String getActiveProperty() { return ACTIVE_PROPERTY; }
    public static String getRequestProperty() { return  REQUEST_PROPERTY; }

    public String getName() {
        return name;
    }
    public String getRequest() {
        return request;
    }
    public Boolean getActive() {
        return active;
    }
    public String getDescription() {
        return description;
    }

    public static StatisticNode fromNode(Neo4jAL neo4jAL, Node node) throws Neo4jBadNodeFormatException {

        if(!node.hasLabel(Label.label(LABEL))) {
            throw new Neo4jBadNodeFormatException(String.format("The node with Id '%d' does not contain the correct label. Expected to have : %s", node.getId(), LABEL), ERROR_PREFIX + "FROMN1");
        }

        try {
            String name = (String) node.getProperty(NAME_PROPERTY);
            boolean active = Neo4jObject.castPropertyToBoolean(node.getProperty(UseCaseNode.getActiveProperty()));
            String request = (String) node.getProperty(REQUEST_PROPERTY);

            String description = "";
            if(node.hasProperty(DESCRIPTION_PROPERTY))
                description = (String) node.getProperty(DESCRIPTION_PROPERTY);

            // Initialize the node
            StatisticNode stn = new StatisticNode(neo4jAL, name, request, active, description);
            stn.setNode(node);

            return stn;
        } catch (NotFoundException | NullPointerException | ClassCastException e) {
            throw new Neo4jBadNodeFormatException(LABEL + " instantiation from node.", ERROR_PREFIX + "FROMN2");
        }
    }

    @Override
    protected Node findNode() throws Neo4jBadRequestException, Neo4jNoResult {
        String initQuery = String.format("MATCH (n:%s) WHERE ID(n)=%d RETURN n as node LIMIT 1;", LABEL, this.getNodeId());
        try {
            Result res = neo4jAL.executeQuery(initQuery);
            Node n = (Node) res.next().get("node");
            this.setNode(n);

            return n;
        } catch (Neo4jQueryException e) {
            throw new Neo4jBadRequestException(LABEL + " node initialization failed", initQuery , e, ERROR_PREFIX+"FIN1");
        } catch (NoSuchElementException |
                NullPointerException e) {
            throw new Neo4jNoResult(String.format("You need to create %s node first.", LABEL),  initQuery, e, ERROR_PREFIX+"FIN2");
        }
    }

    @Override
    public Node createNode() throws Neo4jBadRequestException, Neo4jNoResult {
        String queryDomain = String.format("MERGE (p:%s { %s : \"%s\", %s : \"%s\", %s : %b, %s : \"%s\" }) RETURN p as node;",
                LABEL, NAME_PROPERTY, name, REQUEST_PROPERTY, request, ACTIVE_PROPERTY, this.active, DESCRIPTION_PROPERTY, this.description);
        try {
            Result res = neo4jAL.executeQuery(queryDomain);
            Node n = (Node) res.next().get("node");
            this.setNode(n);
            return n;
        } catch (Neo4jQueryException e) {
            throw new Neo4jBadRequestException(LABEL + " node creation failed", queryDomain , e, ERROR_PREFIX+"CRN1");
        } catch (NoSuchElementException |
                NullPointerException e) {
            throw new Neo4jNoResult(LABEL + "node creation failed",  queryDomain, e, ERROR_PREFIX+"CRN2");
        }
    }

    public static List<StatisticNode> getAllNodes(Neo4jAL neo4jAL) throws Neo4jBadRequestException {
        try {
            List<StatisticNode> resList = new ArrayList<>();
            ResourceIterator<Node> resIt = neo4jAL.findNodes(Label.label(LABEL));
            while ( resIt.hasNext() ) {
                try {
                    Node node = (Node) resIt.next();

                    StatisticNode trn = StatisticNode.fromNode(neo4jAL, node);
                    trn.setNode(node);

                    resList.add(trn);
                }  catch (NoSuchElementException | NullPointerException | Neo4jBadNodeFormatException e) {
                    throw new Neo4jNoResult(LABEL + " nodes retrieving failed",  "findQuery", e, ERROR_PREFIX+"GAN1");
                }
            }
            return resList;
        } catch (Neo4jQueryException | Neo4jNoResult e) {
            throw new Neo4jBadRequestException(LABEL + " nodes retrieving failed", "findQuery" , e, ERROR_PREFIX+"GAN1");
        }
    }

    @Override
    public void deleteNode() throws Neo4jBadRequestException {
        String queryDomain = String.format("MATCH (p:%s) WHERE ID(p)=%d DETACH DELETE p;",
                LABEL, this.getNodeId());
        try {
            neo4jAL.executeQuery(queryDomain);
        } catch (Neo4jQueryException e) {
            throw new Neo4jBadRequestException(LABEL + " node deletion failed", queryDomain , e, ERROR_PREFIX+"DEL1");
        }
    }

    public String executeStat(String applicationLabel) throws Neo4jBadRequestException, Neo4jNoResult {
        if(this.getNode() == null)
            throw new Neo4jBadRequestException("Cannot execute this action. Associated node does not exist.", ERROR_PREFIX+"EXEC1");


        try {
            String forgedReq = TagProcessing.processApplicationContext(this.request, applicationLabel);
            forgedReq = TagProcessing.processAll(forgedReq);

            neo4jAL.logInfo("Processing statistic request : " + forgedReq);

            Result res =  neo4jAL.executeQuery(forgedReq);

            StringBuilder resultString = new StringBuilder();

            while(res.hasNext()) {
                resultString.append("\t - " + res.next().get(STAT_RETURN_STRING));
                resultString.append("\n");
            }

            return resultString.toString();

        } catch (Neo4jQueryException | Neo4JTemplateLanguageException | Exception e) {
            neo4jAL.logError("An error occurred trying to process StatNode with ID" + this.getNodeId(), e);
            throw new Neo4jBadRequestException("The request failed to execute.", this.request, e, ERROR_PREFIX+"EXEC2");
        }
    }


    public StatisticNode(Neo4jAL neo4jAL, String name, String request, Boolean active, String description) {
        super(neo4jAL);
        this.name = name;
        this.request = request;
        this.active = active;
        this.description = description;
    }
}