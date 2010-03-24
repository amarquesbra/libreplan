/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.scenarios;

import static org.navalplanner.web.I18nHelper._;

import java.util.List;

import org.apache.commons.lang.Validate;
import org.hibernate.validator.InvalidValue;
import org.navalplanner.business.calendars.entities.BaseCalendar;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.scenarios.daos.IScenarioDAO;
import org.navalplanner.business.scenarios.entities.Scenario;
import org.navalplanner.web.common.concurrentdetection.OnConcurrentModification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model for UI operations related to {@link Scenario}.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Qualifier("main")
@OnConcurrentModification(goToPage = "/scenarios/scenarios.zul")
public class ScenarioModel implements IScenarioModel {

    /**
     * Conversation state
     */
    private Scenario scenario;

    @Autowired
    private IScenarioDAO scenarioDAO;

    /*
     * Non conversational steps
     */
    @Override
    @Transactional(readOnly = true)
    public List<Scenario> getScenarios() {
        return scenarioDAO.getAll();
    }

    /*
     * Initial conversation steps
     */
    @Override
    @Transactional(readOnly = true)
    public void initEdit(Scenario scenario) {
        Validate.notNull(scenario);
        forceLoad(scenario);

        this.scenario = scenario;
    }

    private void forceLoad(Scenario scenario) {
        scenarioDAO.reattach(scenario);
        scenario.getOrders().keySet();
    }

    @Override
    @Transactional(readOnly = true)
    public void initCreateDerived(Scenario scenario) {
        Validate.notNull(scenario);
        forceLoad(scenario);

        this.scenario = scenario.newDerivedScenario();
    }

    /*
     * Intermediate conversation steps
     */
    @Override
    public Scenario getScenario() {
        return scenario;
    }

    /*
     * Final conversation steps
     */

    @Override
    @Transactional
    public void confirmSave() throws ValidationException {
        if (scenarioDAO.thereIsOtherWithSameName(scenario)) {
            InvalidValue[] invalidValues = { new InvalidValue(_(
                    "{0} already exists", scenario.getName()),
                    BaseCalendar.class, "name", scenario.getName(), scenario) };
            throw new ValidationException(invalidValues,
                    _("Could not save the scenario"));
        }

        scenarioDAO.save(scenario);
    }

    @Override
    public void cancel() {
        resetState();
    }

    private void resetState() {
        scenario = null;
    }

}
