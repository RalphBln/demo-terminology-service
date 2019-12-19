/**
 * Copyright © 2018 Mayo Clinic (RSTKNOWLEDGEMGMT@mayo.edu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.omg.demo.terms.config;

import static edu.mayo.kmdp.id.helper.DatatypeHelper.uri;
import static edu.mayo.kmdp.id.helper.DatatypeHelper.vuri;
import static edu.mayo.kmdp.registry.Registry.BASE_UUID_URN;
import static edu.mayo.ontology.taxonomies.kao.knowledgeassetcategory.KnowledgeAssetCategorySeries.Terminology_Ontology_And_Assertional_KBs;
import static edu.mayo.ontology.taxonomies.kao.knowledgeassettype.KnowledgeAssetTypeSeries.Formal_Ontology;
import static edu.mayo.ontology.taxonomies.krformat.SerializationFormatSeries.XML_1_1;
import static edu.mayo.ontology.taxonomies.krlanguage.KnowledgeRepresentationLanguageSeries.OWL_2;
import static edu.mayo.ontology.taxonomies.krserialization.KnowledgeRepresentationLanguageSerializationSeries.RDF_XML_Syntax;
import static org.omg.spec.api4kp._1_0.AbstractCarrier.canonicalRepresentationOf;

import edu.mayo.kmdp.metadata.surrogate.ComputableKnowledgeArtifact;
import edu.mayo.kmdp.metadata.surrogate.KnowledgeAsset;
import edu.mayo.kmdp.metadata.surrogate.Representation;
import edu.mayo.kmdp.repository.asset.KnowledgeAssetRepositoryService;
import edu.mayo.ontology.taxonomies.lexicon.LexiconSeries;
import java.net.URI;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import org.omg.spec.api4kp._1_0.AbstractCarrier;
import org.omg.spec.api4kp._1_0.identifiers.URIIdentifier;
import org.omg.spec.api4kp._1_0.services.KPServer;
import org.omg.spec.api4kp._1_0.services.KnowledgeCarrier;

@Named
public class TermsTestHelper {

  @Inject
  @KPServer
  KnowledgeAssetRepositoryService assetRepo;

  public static final URI ENDPOINT_LOCAL_VIRTUOSO     = URI.create("http://localhost:8890/sparql/");
  public static final URI ENDPOINT_DBPEDIA            = URI.create("https://dbpedia.org/sparql");
  public static final String LOCAL_PATH               = "/eswc20060921.rdf";

  public static final URI ESWC_ONTOLOGY_URI           = URI.create("http://www.eswc2006.org/technologies/ontology");
  public static final UUID ESWC_ASSET_UUID            = UUID.nameUUIDFromBytes(ESWC_ONTOLOGY_URI.toString().getBytes());

  public static final URI DBPEDIA_ONTOLOGY_URI        = URI.create("https://dbpedia.org");
  public static final UUID DBPEDIA_ASSET_UUID         = UUID.nameUUIDFromBytes(DBPEDIA_ONTOLOGY_URI.toString().getBytes());

  public static final String ONTOLOGY_VERSION = "1.0.0";

  public void initializeRepositoryContent() {
    addLocalOntology();
    addDBPediaVocabulary();
  }



  public void addDBPediaVocabulary() {

    UUID artifactUUID = UUID.nameUUIDFromBytes(DBPEDIA_ONTOLOGY_URI.toString().getBytes());

    URIIdentifier testOntologyId = vuri(
        BASE_UUID_URN + DBPEDIA_ASSET_UUID,
        BASE_UUID_URN + DBPEDIA_ASSET_UUID + ":" + ONTOLOGY_VERSION
    );

    URIIdentifier testDocumentId = vuri(
        BASE_UUID_URN + artifactUUID,
        BASE_UUID_URN + artifactUUID + ":" + ONTOLOGY_VERSION
    );

    KnowledgeAsset metadata = new KnowledgeAsset()
        .withAssetId(testOntologyId)
        .withSecondaryId(uri(DBPEDIA_ONTOLOGY_URI.toString(), ONTOLOGY_VERSION))
        .withName("DBPedia")
        .withDescription("DBPedia Vocabularies")
        .withFormalCategory(Terminology_Ontology_And_Assertional_KBs)
        .withFormalType(Formal_Ontology)
        .withCarriers(new ComputableKnowledgeArtifact()
            .withArtifactId(testDocumentId)
            .withRepresentation(new Representation()
                .withLanguage(OWL_2)
                .withSerialization(RDF_XML_Syntax)
                .withFormat(XML_1_1)
                .withLexicon(LexiconSeries.SKOS))
            .withLocator(ENDPOINT_DBPEDIA)
        );


    assetRepo.publish(metadata,null);
  }


  public void addLocalOntology() {

    UUID artifactUUID = UUID.nameUUIDFromBytes(LOCAL_PATH.getBytes());

    URIIdentifier testOntologyId = vuri(
        BASE_UUID_URN + ESWC_ASSET_UUID,
        BASE_UUID_URN + ESWC_ASSET_UUID + ":" + ONTOLOGY_VERSION
    );

    URIIdentifier testDocumentId = vuri(
        BASE_UUID_URN + artifactUUID,
        BASE_UUID_URN + artifactUUID + ":" + ONTOLOGY_VERSION
    );

    KnowledgeAsset metadata = new KnowledgeAsset()
        .withAssetId(testOntologyId)
        .withSecondaryId(uri(ESWC_ONTOLOGY_URI.toString(), ONTOLOGY_VERSION))
        .withName("Test")
        .withDescription("A test vocabulary")
        .withFormalCategory(Terminology_Ontology_And_Assertional_KBs)
        .withFormalType(Formal_Ontology)
        .withCarriers(new ComputableKnowledgeArtifact()
            .withArtifactId(testDocumentId)
            .withRepresentation(new Representation()
                .withLanguage(OWL_2)
                .withSerialization(RDF_XML_Syntax)
                .withFormat(XML_1_1))
            .withLocator(URI.create(LOCAL_PATH))
        );

    KnowledgeCarrier carrier = AbstractCarrier
        .of(TermsTestHelper.class.getResourceAsStream(LOCAL_PATH))
        .withAssetId(testOntologyId)
        .withArtifactId(testDocumentId)
        .withRepresentation(canonicalRepresentationOf(metadata));

    assetRepo.publish(metadata,carrier);
  }

}
