/**
 * Copyright © 2018 Mayo Clinic (RSTKNOWLEDGEMGMT@mayo.edu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.omg.demo.terms;

import static edu.mayo.ontology.taxonomies.api4kp.parsinglevel.ParsingLevelSeries.Parsed_Knowedge_Expression;
import static edu.mayo.ontology.taxonomies.kao.knowledgeassettype.KnowledgeAssetTypeSeries.Formal_Ontology;
import static edu.mayo.ontology.taxonomies.krlanguage.KnowledgeRepresentationLanguageSeries.SPARQL_1_1;
import static org.omg.spec.api4kp._1_0.AbstractCarrier.rep;
import static org.omg.spec.api4kp._1_0.id.IdentifierConstants.VERSION_ZERO;

import edu.mayo.kmdp.inference.v4.server.QueryApiInternal._askQuery;
import edu.mayo.kmdp.knowledgebase.v4.server.BindingApiInternal._bind;
import edu.mayo.kmdp.knowledgebase.v4.server.KnowledgeBaseApiInternal;
import edu.mayo.kmdp.metadata.v2.surrogate.ComputableKnowledgeArtifact;
import edu.mayo.kmdp.metadata.v2.surrogate.KnowledgeAsset;
import edu.mayo.kmdp.metadata.v2.surrogate.SurrogateBuilder;
import edu.mayo.kmdp.repository.asset.KnowledgeAssetRepositoryService;
import edu.mayo.kmdp.terms.impl.model.ConceptDescriptor;
import edu.mayo.kmdp.terms.v4.server.TermsApiInternal;
import edu.mayo.kmdp.tranx.v4.server.DeserializeApiInternal;
import edu.mayo.kmdp.util.Util;
import edu.mayo.ontology.taxonomies.lexicon.LexiconSeries;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import org.omg.demo.terms.config.TermsPublisher;
import org.omg.demo.terms.internal.TermsBuilder;
import org.omg.demo.terms.internal.TermsQueryType;
import org.omg.spec.api4kp._1_0.AbstractCarrier;
import org.omg.spec.api4kp._1_0.Answer;
import org.omg.spec.api4kp._1_0.datatypes.Bindings;
import org.omg.spec.api4kp._1_0.id.Pointer;
import org.omg.spec.api4kp._1_0.id.ResourceIdentifier;
import org.omg.spec.api4kp._1_0.identifiers.ConceptIdentifier;
import org.omg.spec.api4kp._1_0.services.KPServer;
import org.omg.spec.api4kp._1_0.services.KPSupport;
import org.omg.spec.api4kp._1_0.services.KnowledgeBase;
import org.omg.spec.api4kp._1_0.services.KnowledgeCarrier;
import org.omg.spec.api4kp._1_0.services.SyntacticRepresentation;
import org.springframework.beans.factory.BeanInitializationException;

@Named
@KPServer
public class TermsServer implements TermsApiInternal {

  @Inject
  @KPServer
  // The catalog provides metadata for the terminologies that this server can leverage
  private KnowledgeAssetRepositoryService knowledgeAssetCatalog;

  @Inject
  // Load and perform reasoning with terminology systems
  private KnowledgeBaseApiInternal termsKBManager;

  @Inject
  @KPSupport(SPARQL_1_1)
  private _bind binder;

  @Inject
  private _askQuery inquirer;

  @Inject
  @KPSupport(SPARQL_1_1)
  // Parse...
  private DeserializeApiInternal._applyLift sparqlParser;

  @Inject
  private TermsBuilder termsBuilder;

  @Inject
  private TermsPublisher initializer;

  public TermsServer() {
    //
  }

  @PostConstruct
  public void populateOnInit() {
    initializer.initializeRepositoryContent();
  }

  @Override
  public Answer<List<Pointer>> listTerminologies() {
    return
        knowledgeAssetCatalog.listKnowledgeAssets(
            Formal_Ontology.getTag(),
            null,
            null,
            0, -1);
  }

  @Override
  public Answer<ConceptDescriptor> getTerm(UUID vocabularyId, String versionTag, String conceptId) {
    return Answer.unsupported();
  }

  @Override
  public Answer<List<ConceptDescriptor>> getTerms(
      UUID vocabularyId, String versionTag,
      String labelFilter) {
    return knowledgeAssetCatalog.getKnowledgeAssetVersion(vocabularyId, versionTag)
        .flatMap(vocabularyMetadata -> getTermsForVocabulary(vocabularyMetadata, labelFilter));
  }

  @Override
  public Answer<KnowledgeCarrier> getVocabulary(UUID vocabularyId, String versionTag,
      String xAccept) {
    return Answer.unsupported();
  }

  @Override
  public Answer<Void> isMember(UUID vocabularyId, String versionTag, String conceptExpression) {
    return Answer.unsupported();
  }


  private Answer<List<ConceptDescriptor>> getTermsForVocabulary(KnowledgeAsset vocabularyMetadata,
      String labelFilter) {
    TermsQueryType queryType = detectQueryType(vocabularyMetadata);
    return termsKBManager.initKnowledgeBase(vocabularyMetadata)
        .flatMap(kBaseId ->
            getQuery(vocabularyMetadata, labelFilter, queryType)
                .flatMap(boundQuery ->
                    doQuery(kBaseId, boundQuery, queryType)));
  }


  private Answer<List<ConceptDescriptor>> doQuery(
      ResourceIdentifier kBaseId,
      KnowledgeCarrier query,
      TermsQueryType queryType) {
    return inquirer.askQuery(kBaseId.getUuid(), kBaseId.getVersionTag(), query)
        .map(answer -> termsBuilder.buildTerms(answer, queryType));
  }


  private Answer<KnowledgeCarrier> getQuery(
      KnowledgeAsset vocMetadata,
      String labelFilter,
      TermsQueryType queryType) {
    KnowledgeCarrier paramQuery = loadParametricQuery(queryType.getSourceURL());
    Bindings bindings = getBindings(vocMetadata, labelFilter);

    //TODO Should binding variables to a query more lightweight than setting up a KB?
    // Or should the parametric query be kept as a named KB, and bound & returned each time? <-- pref.

    ResourceIdentifier kbId = paramQuery.getAssetId();
    Answer<KnowledgeBase> paramQueryKb =
        termsKBManager.getKnowledgeBase(kbId.getUuid(), kbId.getVersionTag());

    if (!paramQueryKb.isSuccess()) {
      termsKBManager.initKnowledgeBase(new KnowledgeAsset()
          .withAssetId(paramQuery.getAssetId()))
          .flatMap(newKbId ->
              termsKBManager.populateKnowledgeBase(newKbId.getUuid(), newKbId.getVersionTag(), paramQuery));
    }

    // TODO fix the identifiers so that this chain is simpler and smoother
    return binder.bind(kbId.getUuid(), kbId.getVersionTag(), bindings)
        .flatMap(queryBasekbId -> termsKBManager
            .getKnowledgeBase(queryBasekbId.getUuid(),queryBasekbId.getVersionTag()))
        .map(KnowledgeBase::getManifestation);
  }

  private KnowledgeCarrier loadParametricQuery(String path) {
    KnowledgeCarrier binary = AbstractCarrier
        .of(TermsServer.class.getResourceAsStream(path))
        .withRepresentation(rep(SPARQL_1_1))
        .withAssetId(SurrogateBuilder.assetId(Util.uuid(path), VERSION_ZERO));

    return sparqlParser.applyLift(binary, Parsed_Knowedge_Expression,null,null)
        // TODO : carrying over the IDs is a responsibility of the lifter
        .map(kc -> kc.withAssetId(binary.getAssetId()))
        .orElseThrow(
            () -> new BeanInitializationException("Unable to load necessary query from " + path));
  }

  private Bindings getBindings(KnowledgeAsset vocMetadata, String labelFilter) {
    Bindings bindings = new Bindings();
    if (labelFilter != null) {
      bindings.put("?label", labelFilter);
    }
    bindings.put("?vocabulary",
        ((ResourceIdentifier) vocMetadata.getSecondaryId().get(0)).getResourceId());
    return bindings;
  }

  private TermsQueryType detectQueryType(KnowledgeAsset vocMetadata) {
    // TODO: discuss how to generalize this
    ComputableKnowledgeArtifact cka =
        (ComputableKnowledgeArtifact) vocMetadata.getCarriers().get(0);
    SyntacticRepresentation representation = cka.getRepresentation();

    return Arrays.stream(TermsQueryType.values())
        .flatMap(queryType -> queryType.appliesTo(cka.getLocator()))
        .findAny()
        .orElse(representation.getLexicon().contains(LexiconSeries.SKOS)
            ? TermsQueryType.SKOS : TermsQueryType.OWL2);
  }


}
