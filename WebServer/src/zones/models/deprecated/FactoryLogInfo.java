package zones.models.deprecated;

import java.util.Date;

import models.AbstractLog;
import models.factory.AbstractFactoryLog;

/**
 * Classe do Model FactoryLogInfo do design pattern MVC + Abstract Factory.
 * @author Grupo ECP7AN-MCA1-09 - Bruno Gama, Guilherme Sant'Clair, Luis Felipe, Rafael Cassiolato, Raiza Morata.
 */

public class FactoryLogInfo extends AbstractFactoryLog
{
    
    @Override
    public AbstractLog retornaLogs(Date data, String text)
    {
        AbstractLog logline = new LogInfo(data, text);
        return logline;
    }

}
