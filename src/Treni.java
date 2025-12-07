import java.util.*;

//CLASSE TRENO CHE è L'EFFETTIVO THREAD.
public class Treni extends Thread {

    int ID;
    int vagoni;
    int tempo_in_stazione;
    int tempo_di_arrivo;
    int tempo_giro_largo = 0;
    boolean scarica = false;
    boolean entrata_di_priorita = false;

    double priorita;
    int starving = 0;

    private final StazioneTreni stazione;

    Treni(int ID, StazioneTreni stazione) {
        this.stazione = stazione;

        Random rand = new Random();

        this.ID = ID;
        this.vagoni = rand.nextInt(1, 10) + 1;
        this.tempo_di_arrivo = rand.nextInt(1, 15) + 1;
        this.tempo_in_stazione = this.vagoni * 2;
        this.priorita = (double) this.vagoni * this.tempo_di_arrivo;
    }

    //ZONA IN CUI IL TRENO SIMULA IL SUO STATO,LE FUNZIONI SONO ALL'INTERNO DELLA CLASSE STAZIONE.
    @Override
    public void run()
    {
        while (true)
        {
            stazione.simulaTreno(this);

            if (!stazione.possibileEntrataInStazione(this))continue;

            stazione.inizioScaricoMerci(this);
            break;
        }
    }
}
