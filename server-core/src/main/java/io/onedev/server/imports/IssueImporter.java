package io.onedev.server.imports;

import java.io.Serializable;

import javax.annotation.Nullable;

import io.onedev.server.model.Project;
import io.onedev.server.util.SimpleLogger;

public abstract class IssueImporter<Where extends Serializable,  What extends Serializable, How extends Serializable> 
		extends Importer<Where, What, How> {

	private static final long serialVersionUID = 1L;
	
	@Nullable
	public abstract String doImport(Where where, What what, How how, Project project, 
			boolean dryRun, SimpleLogger logger);
	
}
